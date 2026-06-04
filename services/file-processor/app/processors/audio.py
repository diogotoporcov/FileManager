import asyncio
import json
import logging
import math
import subprocess
import tempfile
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.processors.audio_mime_types import is_processable_audio_mime_type, parse_processable_audio_mime_types
from app.processors.base import Processor, ProcessorResult
from app.processors.video_mime_types import is_processable_video_mime_type, parse_processable_video_mime_types
from app.storage.base import StorageObjectReader
from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class AudioMetadata:
    duration_seconds: float
    codec: str
    sample_rate: int
    channels: int
    bit_rate: int | None
    audio_stream_index: int | None
    container_format: str | None


@dataclass(frozen=True, slots=True)
class AudioFingerprint:
    fingerprint: str
    duration_seconds: int


class AudioFingerprintProcessor(Processor):
    def __init__(
        self,
        storage_reader: StorageObjectReader,
        *,
        max_file_bytes: int | None = None,
        max_duration_seconds: float | None = None,
        fingerprint_length_seconds: int | None = None,
        subprocess_timeout_seconds: float | None = None,
        max_fingerprint_chars: int | None = None,
        fingerprint_version: str = "fpcalc",
    ):
        self.storage_reader = storage_reader
        self.max_file_bytes = max_file_bytes or settings.worker_audio_max_file_bytes
        self.max_duration_seconds = max_duration_seconds or settings.worker_audio_max_duration_seconds
        self.fingerprint_length_seconds = fingerprint_length_seconds or settings.worker_audio_fingerprint_length_seconds
        self.subprocess_timeout_seconds = subprocess_timeout_seconds or settings.worker_audio_subprocess_timeout_seconds
        self.max_fingerprint_chars = max_fingerprint_chars or settings.worker_audio_max_fingerprint_chars
        self.fingerprint_version = fingerprint_version
        self.processable_audio_mime_types = parse_processable_audio_mime_types(
            settings.worker_audio_supported_mime_types
        )
        self.processable_video_mime_types = parse_processable_video_mime_types(
            settings.worker_video_supported_mime_types
        )

    @property
    def name(self) -> str:
        return "audio_analysis"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        if event.job_type.upper() != "AUDIO_ANALYSIS":
            return False

        if is_processable_audio_mime_type(
            event.mime_type,
            self.processable_audio_mime_types,
        ):
            return settings.worker_audio_enabled and settings.worker_audio_fingerprint_enabled

        if is_processable_video_mime_type(
            event.mime_type,
            self.processable_video_mime_types,
        ):
            return settings.worker_audio_enabled and settings.worker_video_audio_analysis_enabled

        return False

    async def process(self, event: FileProcessingRequestedEvent) -> ProcessorResult:
        if is_processable_video_mime_type(event.mime_type, self.processable_video_mime_types):
            if not settings.worker_audio_enabled or not settings.worker_video_audio_analysis_enabled:
                raise NonRetryableProcessingError("Video audio-track analysis is disabled")
        elif is_processable_audio_mime_type(event.mime_type, self.processable_audio_mime_types):
            if not settings.worker_audio_enabled or not settings.worker_audio_fingerprint_enabled:
                raise NonRetryableProcessingError("Standalone audio fingerprint processing is disabled")

        if event.size > self.max_file_bytes:
            raise NonRetryableProcessingError("Audio exceeds maximum processing size")

        audio_path = await self._spool_audio(event)

        try:
            metadata = await asyncio.to_thread(self._probe_metadata, audio_path)
            self._validate_metadata(metadata)
            fingerprint = await asyncio.to_thread(self._fingerprint, audio_path)

            logger.info("Computed audio fingerprint for file %s", event.file_id)
            return {
                "durationMs": round(metadata.duration_seconds * 1000),
                "codec": metadata.codec,
                "sampleRate": metadata.sample_rate,
                "channels": metadata.channels,
                "bitRate": metadata.bit_rate,
                "audioStreamIndex": metadata.audio_stream_index,
                "containerFormat": metadata.container_format,
                "fingerprint": fingerprint.fingerprint,
                "fingerprintAlgorithm": "chromaprint",
                "fingerprintVersion": self.fingerprint_version,
                "fingerprintDurationSeconds": fingerprint.duration_seconds,
            }

        finally:
            audio_path.unlink(missing_ok=True)

    async def _spool_audio(self, event: FileProcessingRequestedEvent) -> Path:
        total_bytes = 0
        temp = tempfile.NamedTemporaryFile(
            prefix="filemanager-audio-",
            suffix=_safe_media_suffix(event.mime_type),
            delete=False,
        )
        temp_path = Path(temp.name)

        try:
            with temp:
                async for chunk in self.storage_reader.read_content(event.storage_reference):
                    total_bytes += len(chunk)
                    if total_bytes > self.max_file_bytes:
                        raise NonRetryableProcessingError("Audio exceeds maximum processing size")

                    temp.write(chunk)

            if total_bytes == 0:
                raise NonRetryableProcessingError("Audio content is empty")

            return temp_path

        except NonRetryableProcessingError:
            temp_path.unlink(missing_ok=True)
            raise

        except Exception as exc:
            temp_path.unlink(missing_ok=True)
            raise RetryableProcessingError(f"Failed to read audio from storage: {type(exc).__name__}") from exc

    def _probe_metadata(self, audio_path: Path) -> AudioMetadata:
        completed = self._run_subprocess([
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "a:0",
            "-show_entries",
            "format=format_name,duration,bit_rate:stream=index,codec_name,sample_rate,channels,bit_rate,duration",
            "-of",
            "json",
            str(audio_path),
        ])

        try:
            payload = json.loads(completed.stdout.decode("utf-8"))
            streams = payload.get("streams") or []
            if not streams:
                raise NonRetryableProcessingError("No audio stream found")

            stream = streams[0]
            format_payload = payload.get("format") or {}
            duration = _parse_float(stream.get("duration")) or _parse_float(format_payload.get("duration"))
            codec = _parse_str(stream.get("codec_name"), max_length=255)
            sample_rate = _parse_int(stream.get("sample_rate"))
            channels = _parse_int(stream.get("channels"))

            if duration is None:
                raise NonRetryableProcessingError("Audio duration is unavailable")
            if codec is None:
                raise NonRetryableProcessingError("Audio codec is unavailable")
            if sample_rate is None:
                raise NonRetryableProcessingError("Audio sample rate is unavailable")
            if channels is None:
                raise NonRetryableProcessingError("Audio channel count is unavailable")

            return AudioMetadata(
                duration_seconds=duration,
                codec=codec,
                sample_rate=sample_rate,
                channels=channels,
                bit_rate=_parse_int(stream.get("bit_rate")) or _parse_int(format_payload.get("bit_rate")),
                audio_stream_index=_parse_int(stream.get("index")),
                container_format=_parse_str(format_payload.get("format_name"), max_length=255),
            )

        except NonRetryableProcessingError:
            raise

        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise NonRetryableProcessingError("Unable to parse audio metadata") from exc

    def _fingerprint(self, audio_path: Path) -> AudioFingerprint:
        json_args = [
            "fpcalc",
            "-json",
            "-length",
            str(self.fingerprint_length_seconds),
            str(audio_path),
        ]
        legacy_args = [
            "fpcalc",
            "-length",
            str(self.fingerprint_length_seconds),
            str(audio_path),
        ]

        try:
            completed = self._run_subprocess(json_args)
            return self._parse_fingerprint_json(completed.stdout)

        except NonRetryableProcessingError as exc:
            if "executable is unavailable" in str(exc) or "timed out" in str(exc):
                raise

            completed = self._run_subprocess(legacy_args)
            return self._parse_fingerprint_text(completed.stdout)

    def _parse_fingerprint_json(self, stdout: bytes) -> AudioFingerprint:
        try:
            payload = json.loads(stdout.decode("utf-8"))
            fingerprint = _parse_str(payload.get("fingerprint"), max_length=self.max_fingerprint_chars)
            duration_seconds = _parse_int(payload.get("duration"))

            if fingerprint is None:
                raise NonRetryableProcessingError("Audio fingerprint output is empty")
            if duration_seconds is None or duration_seconds <= 0:
                raise NonRetryableProcessingError("Audio fingerprint duration is invalid")

            return AudioFingerprint(fingerprint=fingerprint, duration_seconds=duration_seconds)

        except NonRetryableProcessingError:
            raise

        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise NonRetryableProcessingError("Unable to parse audio fingerprint output") from exc

    def _parse_fingerprint_text(self, stdout: bytes) -> AudioFingerprint:
        try:
            values: dict[str, str] = {}
            for line in stdout.decode("utf-8").splitlines():
                key, separator, value = line.partition("=")
                if separator:
                    values[key.strip().upper()] = value.strip()

            fingerprint = _parse_str(values.get("FINGERPRINT"), max_length=self.max_fingerprint_chars)
            duration_seconds = _parse_int(values.get("DURATION"))

            if fingerprint is None:
                raise NonRetryableProcessingError("Audio fingerprint output is empty")
            if duration_seconds is None or duration_seconds <= 0:
                raise NonRetryableProcessingError("Audio fingerprint duration is invalid")

            return AudioFingerprint(fingerprint=fingerprint, duration_seconds=duration_seconds)

        except NonRetryableProcessingError:
            raise

        except (UnicodeDecodeError, ValueError) as exc:
            raise NonRetryableProcessingError("Unable to parse audio fingerprint output") from exc

    def _run_subprocess(self, args: Sequence[str]) -> subprocess.CompletedProcess[bytes]:
        try:
            return subprocess.run(
                args,
                check=True,
                capture_output=True,
                shell=False,
                timeout=self.subprocess_timeout_seconds,
            )

        except FileNotFoundError as exc:
            executable = args[0] if args else "media tool"
            raise NonRetryableProcessingError(f"{executable} executable is unavailable") from exc

        except subprocess.TimeoutExpired as exc:
            raise NonRetryableProcessingError("Audio media command timed out") from exc

        except subprocess.CalledProcessError as exc:
            raise NonRetryableProcessingError("Audio media command failed") from exc

    def _validate_metadata(self, metadata: AudioMetadata) -> None:
        if not math.isfinite(metadata.duration_seconds) or metadata.duration_seconds <= 0:
            raise NonRetryableProcessingError("Audio duration must be positive")

        if metadata.duration_seconds > self.max_duration_seconds:
            raise NonRetryableProcessingError("Audio exceeds maximum processing duration")

        if metadata.sample_rate <= 0:
            raise NonRetryableProcessingError("Audio sample rate must be positive")

        if metadata.channels <= 0:
            raise NonRetryableProcessingError("Audio channel count must be positive")


def _parse_float(value: Any) -> float | None:
    if value is None or value == "N/A":
        return None

    parsed = float(value)
    return parsed if math.isfinite(parsed) else None


def _parse_int(value: Any) -> int | None:
    if value is None or value == "N/A":
        return None

    parsed = int(value)
    return parsed if parsed >= 0 else None


def _parse_str(value: Any, *, max_length: int) -> str | None:
    if value is None:
        return None

    parsed = str(value).strip()
    if not parsed or len(parsed) > max_length:
        return None

    return parsed


def _safe_media_suffix(mime_type: str | None) -> str:
    normalized = (mime_type or "").split(";", 1)[0].strip().lower()
    suffixes = {
        "audio/mpeg": ".mp3",
        "audio/mp3": ".mp3",
        "audio/wav": ".wav",
        "audio/x-wav": ".wav",
        "audio/wave": ".wav",
        "audio/vnd.wave": ".wav",
        "audio/flac": ".flac",
        "audio/x-flac": ".flac",
        "audio/ogg": ".ogg",
        "audio/aac": ".aac",
        "audio/mp4": ".m4a",
        "audio/x-m4a": ".m4a",
        "audio/webm": ".webm",
        "audio/opus": ".opus",
        "audio/matroska": ".mka",
        "audio/x-matroska": ".mka",
        "audio/ac3": ".ac3",
        "audio/3gpp": ".3gp",
        "audio/3gpp2": ".3g2",
        "audio/x-aiff": ".aiff",
        "audio/aiff": ".aiff",
        "video/mp4": ".mp4",
        "video/webm": ".webm",
        "video/quicktime": ".mov",
        "video/x-msvideo": ".avi",
        "video/avi": ".avi",
        "video/matroska": ".mkv",
        "video/x-matroska": ".mkv",
        "video/x-m4v": ".m4v",
        "video/mpeg": ".mpeg",
        "video/mp2t": ".ts",
        "video/3gpp": ".3gp",
        "video/3gpp2": ".3g2",
    }
    return suffixes.get(normalized, ".media")
