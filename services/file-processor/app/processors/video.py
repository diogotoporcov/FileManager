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

import imagehash
import numpy as np
from PIL import Image, UnidentifiedImageError

from app.config import settings
from app.embeddings.base import (
    ImageEmbeddingInferenceClient,
    ImageEmbeddingModelOutputError,
    ImageEmbeddingServiceUnavailable,
)
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor, ProcessorResult, ProcessorResultValue
from app.processors.embedding import normalize_embedding_image, preprocess_clip_image
from app.processors.video_mime_types import is_processable_video_mime_type, parse_processable_video_mime_types
from app.storage.base import StorageObjectReader
from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class VideoMetadata:
    duration_seconds: float
    width: int | None
    height: int | None
    frame_count: int | None
    codec: str | None


@dataclass(frozen=True, slots=True)
class SampledVideoFrame:
    timestamp_ms: int
    frame_index: int
    phash: str | None
    embedding: Sequence[float] | None


class VideoAnalysisProcessor(Processor):
    def __init__(
        self,
        storage_reader: StorageObjectReader,
        embedding_client: ImageEmbeddingInferenceClient | None,
        *,
        model_name: str | None = None,
        model_version: str | None = None,
        embedding_dimension: int | None = None,
        input_size: int | None = None,
        max_file_bytes: int | None = None,
        max_duration_seconds: float | None = None,
        min_sampled_frames: int | None = None,
        max_sampled_frames: int | None = None,
        target_interval_seconds: float | None = None,
        frame_timeout_seconds: float | None = None,
        max_frame_bytes: int | None = None,
    ):
        self.storage_reader = storage_reader
        self.embedding_client = embedding_client
        self.model_name = model_name or settings.embedding_model_name
        self.model_version = model_version or settings.embedding_model_version
        self.embedding_dimension = embedding_dimension or settings.embedding_dimension
        self.input_size = input_size or settings.embedding_image_input_size
        self.max_file_bytes = max_file_bytes or settings.worker_video_max_file_bytes
        self.max_duration_seconds = max_duration_seconds or settings.worker_video_max_duration_seconds
        self.min_sampled_frames = min_sampled_frames or settings.worker_video_min_sampled_frames
        self.max_sampled_frames = max_sampled_frames or settings.worker_video_max_sampled_frames
        self.target_interval_seconds = target_interval_seconds or settings.worker_video_target_interval_seconds
        self.frame_timeout_seconds = frame_timeout_seconds or settings.worker_video_frame_timeout_seconds
        self.max_frame_bytes = max_frame_bytes or settings.worker_video_max_frame_bytes
        self.processable_video_mime_types = parse_processable_video_mime_types(
            settings.worker_video_supported_mime_types
        )

        if self.min_sampled_frames > self.max_sampled_frames:
            raise ValueError("worker video min sampled frames cannot exceed max sampled frames")

    @property
    def name(self) -> str:
        return "video_analysis"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        if event.job_type.upper() != "VIDEO_ANALYSIS":
            return False

        if not settings.worker_video_enabled or not settings.worker_video_analysis_enabled:
            return False

        if not settings.worker_video_frame_phash_enabled and not settings.worker_video_frame_embedding_enabled:
            return False

        return is_processable_video_mime_type(
            event.mime_type,
            self.processable_video_mime_types,
        )

    async def process(self, event: FileProcessingRequestedEvent) -> ProcessorResult:
        if not settings.worker_video_enabled or not settings.worker_video_analysis_enabled:
            raise NonRetryableProcessingError("Video analysis processing is disabled")

        if not settings.worker_video_frame_phash_enabled and not settings.worker_video_frame_embedding_enabled:
            raise NonRetryableProcessingError("Video analysis has no enabled frame outputs")

        if event.size > self.max_file_bytes:
            raise NonRetryableProcessingError("Video exceeds maximum processing size")

        video_path = await self._spool_video(event)

        try:
            metadata = await asyncio.to_thread(self._probe_metadata, video_path)
            self._validate_metadata(metadata)
            timestamps = sample_timestamps(
                metadata.duration_seconds,
                self.min_sampled_frames,
                self.max_sampled_frames,
                self.target_interval_seconds,
            )
            frames: list[SampledVideoFrame] = []

            for frame_index, timestamp_seconds in enumerate(timestamps):
                frame_image = await asyncio.to_thread(self._extract_frame, video_path, timestamp_seconds)
                phash = self._compute_frame_phash(frame_image) if settings.worker_video_frame_phash_enabled else None
                embedding = await self._embed_frame(frame_image) if settings.worker_video_frame_embedding_enabled else None
                frames.append(
                    SampledVideoFrame(
                        timestamp_ms=round(timestamp_seconds * 1000),
                        frame_index=frame_index,
                        phash=phash,
                        embedding=embedding,
                    )
                )

            logger.info("Computed video analysis for file %s with %s sampled frames", event.file_id, len(frames))
            embedding_dimension = next((len(frame.embedding) for frame in frames if frame.embedding is not None), None)

            result: dict[str, ProcessorResultValue] = {
                "durationMs": round(metadata.duration_seconds * 1000),
                "width": metadata.width,
                "height": metadata.height,
                "frameCount": metadata.frame_count,
                "codec": metadata.codec,
                "sampledFrameCount": len(frames),
                "samplingStrategy": self._sampling_strategy(),
                "frames": [
                    _frame_result(frame)
                    for frame in frames
                ],
            }
            if embedding_dimension is not None:
                result.update({
                    "modelName": self.model_name,
                    "modelVersion": self.model_version,
                    "dimension": embedding_dimension,
                })

            return result

        finally:
            video_path.unlink(missing_ok=True)

    async def _spool_video(self, event: FileProcessingRequestedEvent) -> Path:
        total_bytes = 0
        temp = tempfile.NamedTemporaryFile(prefix="filemanager-video-", suffix=".bin", delete=False)
        temp_path = Path(temp.name)

        try:
            with temp:
                async for chunk in self.storage_reader.read_content(event.storage_reference):
                    total_bytes += len(chunk)
                    if total_bytes > self.max_file_bytes:
                        raise NonRetryableProcessingError("Video exceeds maximum processing size")

                    temp.write(chunk)
            if total_bytes == 0:
                raise NonRetryableProcessingError("Video content is empty")

            return temp_path

        except NonRetryableProcessingError:
            temp_path.unlink(missing_ok=True)
            raise

        except Exception as exc:
            temp_path.unlink(missing_ok=True)
            raise RetryableProcessingError(f"Failed to read video from storage: {type(exc).__name__}") from exc

    def _probe_metadata(self, video_path: Path) -> VideoMetadata:
        completed = self._run_subprocess([
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "format=duration:stream=codec_name,width,height,nb_frames,duration",
            "-of",
            "json",
            str(video_path),
        ])

        try:
            payload = json.loads(completed.stdout.decode("utf-8"))
            stream = (payload.get("streams") or [{}])[0]
            duration = _parse_float(stream.get("duration")) or _parse_float((payload.get("format") or {}).get("duration"))
            if duration is None:
                raise NonRetryableProcessingError("Video duration is unavailable")

            return VideoMetadata(
                duration_seconds=duration,
                width=_parse_int(stream.get("width")),
                height=_parse_int(stream.get("height")),
                frame_count=_parse_int(stream.get("nb_frames")),
                codec=_parse_str(stream.get("codec_name")),
            )

        except NonRetryableProcessingError:
            raise

        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise NonRetryableProcessingError("Unable to parse video metadata") from exc

    def _extract_frame(self, video_path: Path, timestamp_seconds: float) -> Image.Image:
        completed = self._run_subprocess([
            "ffmpeg",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-ss",
            f"{timestamp_seconds:.3f}",
            "-i",
            str(video_path),
            "-frames:v",
            "1",
            "-f",
            "image2pipe",
            "-vcodec",
            "png",
            "pipe:1",
        ])

        if not completed.stdout:
            raise NonRetryableProcessingError("Video frame extraction produced no image data")

        if len(completed.stdout) > self.max_frame_bytes:
            raise NonRetryableProcessingError("Extracted video frame exceeds maximum image size")

        try:
            with tempfile.SpooledTemporaryFile(max_size=min(self.max_frame_bytes, 1024 * 1024)) as frame_buffer:
                frame_buffer.write(completed.stdout)
                frame_buffer.seek(0)
                with Image.open(frame_buffer) as image:
                    image.load()

                    return image.convert("RGB")

        except UnidentifiedImageError as exc:
            raise NonRetryableProcessingError("Video frame extraction produced invalid image data") from exc

    def _run_subprocess(self, args: Sequence[str]) -> subprocess.CompletedProcess[bytes]:
        try:
            return subprocess.run(
                args,
                check=True,
                capture_output=True,
                shell=False,
                timeout=self.frame_timeout_seconds,
            )

        except FileNotFoundError as exc:
            raise NonRetryableProcessingError("ffmpeg/ffprobe executable is unavailable") from exc

        except subprocess.TimeoutExpired as exc:
            raise NonRetryableProcessingError("Video ffmpeg/ffprobe command timed out") from exc

        except subprocess.CalledProcessError as exc:
            raise NonRetryableProcessingError("Video ffmpeg/ffprobe command failed") from exc

    def _validate_metadata(self, metadata: VideoMetadata) -> None:
        if not math.isfinite(metadata.duration_seconds) or metadata.duration_seconds <= 0:
            raise NonRetryableProcessingError("Video duration must be positive")

        if metadata.duration_seconds > self.max_duration_seconds:
            raise NonRetryableProcessingError("Video exceeds maximum processing duration")

        if metadata.width is not None and metadata.width <= 0:
            raise NonRetryableProcessingError("Video width must be positive")

        if metadata.height is not None and metadata.height <= 0:
            raise NonRetryableProcessingError("Video height must be positive")

    @staticmethod
    def _compute_frame_phash(image: Image.Image) -> str:
        phash = str(imagehash.phash(image)).lower()
        if len(phash) != 16 or not all(c in "0123456789abcdef" for c in phash):
            raise NonRetryableProcessingError("Invalid video frame pHash format produced")

        return phash

    async def _embed_frame(self, image: Image.Image) -> Sequence[float]:
        if self.embedding_client is None:
            raise NonRetryableProcessingError("Video frame embedding client is unavailable")

        try:
            prepared = normalize_embedding_image(
                image,
                self.input_size,
                settings.embedding_max_source_pixels,
                settings.embedding_direct_decode_max_pixels,
            )
            pixel_values = preprocess_clip_image(prepared, self.input_size)
            model_output = await self.embedding_client.embed_image(pixel_values)

        except ImageEmbeddingServiceUnavailable as exc:
            raise RetryableProcessingError("Triton image embedding inference unavailable") from exc

        except ImageEmbeddingModelOutputError as exc:
            raise NonRetryableProcessingError("Invalid image embedding model output") from exc

        return self._normalize_model_output(model_output)

    def _normalize_model_output(self, model_output: np.ndarray) -> Sequence[float]:
        output = np.asarray(model_output, dtype=np.float32)
        if output.ndim == 2 and output.shape[0] == 1:
            output = output[0]

        if output.ndim != 1:
            raise NonRetryableProcessingError("Video frame embedding output must be a single vector")

        if output.shape[0] != self.embedding_dimension:
            raise NonRetryableProcessingError("Video frame embedding output dimension mismatch")

        if not np.all(np.isfinite(output)):
            raise NonRetryableProcessingError("Video frame embedding output must contain finite values")

        norm = float(np.linalg.norm(output))
        if not np.isfinite(norm) or norm == 0.0:
            raise NonRetryableProcessingError("Video frame embedding output norm must be finite and non-zero")

        normalized = np.asarray(output / norm, dtype=np.float32)

        return [float(value) for value in normalized.tolist()]

    def _sampling_strategy(self) -> str:
        return (
            f"even_interval:min={self.min_sampled_frames},max={self.max_sampled_frames},"
            f"target_seconds={self.target_interval_seconds:g}"
        )


def _frame_result(frame: SampledVideoFrame) -> dict[str, ProcessorResultValue]:
    result: dict[str, ProcessorResultValue] = {
        "timestampMs": frame.timestamp_ms,
        "frameIndex": frame.frame_index,
    }
    if frame.phash is not None:
        result["phash"] = frame.phash
    if frame.embedding is not None:
        result["embedding"] = list(frame.embedding)

    return result


def sample_timestamps(
    duration_seconds: float,
    min_sampled_frames: int,
    max_sampled_frames: int,
    target_interval_seconds: float,
) -> list[float]:
    if not math.isfinite(duration_seconds) or duration_seconds <= 0:
        raise NonRetryableProcessingError("Video duration must be positive")

    if min_sampled_frames > max_sampled_frames:
        raise NonRetryableProcessingError("Video sampling minimum exceeds maximum")

    sample_count = math.ceil(duration_seconds / target_interval_seconds)
    sample_count = max(min_sampled_frames, min(max_sampled_frames, sample_count))
    sample_count = max(1, sample_count)

    start = min(0.5, duration_seconds / 2)
    end = max(start, duration_seconds - min(0.5, duration_seconds / 2))
    if sample_count == 1:
        return [start]
    if end == start:
        return [start for _ in range(sample_count)]

    interval = (end - start) / (sample_count - 1)

    return [start + (interval * index) for index in range(sample_count)]


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


def _parse_str(value: Any) -> str | None:
    if value is None:
        return None

    parsed = str(value).strip()

    return parsed or None
