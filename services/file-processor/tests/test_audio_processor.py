import json
import subprocess
import uuid
from collections.abc import AsyncIterator
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pytest

from app.events.models import FileProcessingRequestedEvent
from app.processors import audio as audio_module
from app.processors.audio import AudioFingerprintProcessor
from app.storage.base import StorageObjectReader, StorageObjectReference
from app.worker.errors import NonRetryableProcessingError


class AudioStorageReader(StorageObjectReader):
    def __init__(self, chunks: list[bytes] | None = None):
        self.chunks = chunks or [b"audio", b"-bytes"]
        self.references: list[StorageObjectReference] = []

    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        self.references.append(reference)
        for chunk in self.chunks:
            yield chunk


@pytest.fixture
def audio_event() -> FileProcessingRequestedEvent:
    return FileProcessingRequestedEvent(
        event_id=uuid.uuid4(),
        event_type="file.processing.requested",
        occurred_at=datetime.now(timezone.utc),
        file_id=uuid.uuid4(),
        processing_job_id=uuid.uuid4(),
        job_type="AUDIO_ANALYSIS",
        storage_path="objects/audio.mp3",
        mime_type="audio/mpeg",
        size=11,
        owner_user_id=uuid.uuid4(),
    )


def test_audio_processor_selection_supports_audio_only(monkeypatch, audio_event):
    monkeypatch.setattr(audio_module.settings, "worker_audio_supported_mime_types", "audio/mpeg,audio/webm")
    processor = AudioFingerprintProcessor(AudioStorageReader())

    assert processor.should_process(audio_event) is True
    audio_event.mime_type = "video/mp4"
    assert processor.should_process(audio_event) is False
    audio_event.mime_type = "audio/webm"
    assert processor.should_process(audio_event) is True
    audio_event.mime_type = "image/jpeg"
    assert processor.should_process(audio_event) is False
    audio_event.job_type = "CHECKSUM"
    audio_event.mime_type = "audio/mpeg"
    assert processor.should_process(audio_event) is False


def test_audio_processor_selection_supports_configured_audio_mime_types(monkeypatch, audio_event):
    monkeypatch.setattr(
        audio_module.settings,
        "worker_audio_supported_mime_types",
        "audio/webm,audio/opus,audio/matroska,audio/vnd.wave,audio/wave,audio/x-flac,audio/ac3,audio/x-aiff",
    )
    processor = AudioFingerprintProcessor(AudioStorageReader())

    for mime_type in (
        "audio/webm",
        "audio/opus",
        "audio/matroska",
        "audio/vnd.wave",
        "audio/wave",
        "audio/x-flac",
        "audio/ac3",
        "audio/x-aiff",
        " audio/webm ",
        "audio/vnd.wave; codecs=1",
    ):
        audio_event.mime_type = mime_type
        assert processor.should_process(audio_event) is True


@pytest.mark.asyncio
async def test_audio_fingerprint_disabled_rejects_standalone_audio_without_reading(monkeypatch, audio_event):
    monkeypatch.setattr(audio_module.settings, "worker_audio_fingerprint_enabled", False)
    storage = AudioStorageReader()
    processor = AudioFingerprintProcessor(storage)

    assert processor.should_process(audio_event) is False
    with pytest.raises(NonRetryableProcessingError, match="Audio fingerprint processing is disabled"):
        await processor.process(audio_event)

    assert storage.references == []


@pytest.mark.asyncio
async def test_audio_analysis_rejects_video_mime_type_without_reading(monkeypatch, audio_event):
    monkeypatch.setattr(audio_module.settings, "worker_audio_fingerprint_enabled", True)
    audio_event.mime_type = "video/mp4"
    storage = AudioStorageReader()
    processor = AudioFingerprintProcessor(storage)

    assert processor.should_process(audio_event) is False
    with pytest.raises(NonRetryableProcessingError, match="Audio analysis only supports audio MIME types"):
        await processor.process(audio_event)

    assert storage.references == []


@pytest.mark.asyncio
async def test_audio_analysis_uses_storage_reference_argument_lists_length_and_cleans_temp(monkeypatch, audio_event):
    audio_event.mime_type = "audio/wav"
    storage = AudioStorageReader()
    processor = AudioFingerprintProcessor(
        storage,
        max_file_bytes=1024,
        max_duration_seconds=60,
        fingerprint_length_seconds=45,
        subprocess_timeout_seconds=12,
        fingerprint_version="fpcalc-test",
    )
    calls: list[list[str]] = []

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        calls.append(args)
        assert kwargs["shell"] is False
        assert kwargs["timeout"] == 12
        assert isinstance(args, list)
        if args[0] == "ffprobe":
            return subprocess.CompletedProcess(args, 0, stdout=metadata_json(), stderr=b"")

        return subprocess.CompletedProcess(args, 0, stdout=fingerprint_json(), stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    result = await processor.process(audio_event)

    assert storage.references == [StorageObjectReference(path="objects/audio.mp3")]
    assert result == {
        "durationMs": 12_000,
        "codec": "mp3",
        "sampleRate": 44100,
        "channels": 2,
        "bitRate": 128000,
        "audioStreamIndex": 0,
        "containerFormat": "mp3",
        "fingerprint": "12345ABC",
        "fingerprintAlgorithm": "chromaprint",
        "fingerprintVersion": "fpcalc-test",
        "fingerprintDurationSeconds": 45,
    }
    assert calls[0][0] == "ffprobe"
    assert calls[1][:3] == ["fpcalc", "-json", "-length"]
    assert calls[1][3] == "45"
    assert calls[0][-1].endswith(".wav")
    assert not Path(calls[0][-1]).exists()


@pytest.mark.asyncio
async def test_audio_analysis_rejects_video_mime_type_before_probe(monkeypatch, audio_event):
    audio_event.mime_type = "video/mp4"
    processor = AudioFingerprintProcessor(AudioStorageReader(), max_file_bytes=1024)

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        return subprocess.CompletedProcess(args, 0, stdout=json.dumps({"streams": []}).encode("utf-8"), stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(NonRetryableProcessingError, match="Audio analysis only supports audio MIME types"):
        await processor.process(audio_event)


@pytest.mark.asyncio
async def test_audio_analysis_rejects_oversized_event_without_reading(audio_event):
    storage = AudioStorageReader()
    processor = AudioFingerprintProcessor(storage, max_file_bytes=10)
    audio_event.size = 11

    with pytest.raises(NonRetryableProcessingError, match="maximum processing size"):
        await processor.process(audio_event)

    assert storage.references == []


@pytest.mark.asyncio
async def test_audio_analysis_rejects_oversized_stream(audio_event):
    storage = AudioStorageReader([b"a" * 6, b"b" * 6])
    processor = AudioFingerprintProcessor(storage, max_file_bytes=10)
    audio_event.size = 10

    with pytest.raises(NonRetryableProcessingError, match="maximum processing size"):
        await processor.process(audio_event)


@pytest.mark.asyncio
async def test_audio_analysis_rejects_duration_over_limit(monkeypatch, audio_event):
    processor = AudioFingerprintProcessor(
        AudioStorageReader(),
        max_file_bytes=1024,
        max_duration_seconds=1,
    )

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        return subprocess.CompletedProcess(args, 0, stdout=metadata_json(duration=12), stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(NonRetryableProcessingError, match="maximum processing duration"):
        await processor.process(audio_event)


def test_audio_fingerprint_rejects_bounded_output():
    processor = AudioFingerprintProcessor(AudioStorageReader(), max_fingerprint_chars=4)

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        return subprocess.CompletedProcess(args, 0, stdout=fingerprint_json(fingerprint="12345").strip(), stderr=b"")

    with pytest.MonkeyPatch.context() as monkeypatch:
        monkeypatch.setattr(subprocess, "run", fake_run)
        with pytest.raises(NonRetryableProcessingError, match="fingerprint output is empty"):
            processor._fingerprint(Path("audio.bin"))


def test_audio_fingerprint_falls_back_to_legacy_fpcalc_output(monkeypatch):
    processor = AudioFingerprintProcessor(AudioStorageReader(), fingerprint_length_seconds=30)
    calls: list[list[str]] = []

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        calls.append(args)
        if "-json" in args:
            raise subprocess.CalledProcessError(1, args, output=b"", stderr=b"unsupported option")

        return subprocess.CompletedProcess(args, 0, stdout=b"FILE=audio.wav\nDURATION=12\nFINGERPRINT=AQABC\n", stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    result = processor._fingerprint(Path("audio.wav"))

    assert result.fingerprint == "AQABC"
    assert result.duration_seconds == 12
    assert calls[0][:3] == ["fpcalc", "-json", "-length"]
    assert calls[1][:2] == ["fpcalc", "-length"]
    assert "-json" not in calls[1]


def test_audio_subprocess_timeout_is_non_retryable(monkeypatch):
    processor = AudioFingerprintProcessor(AudioStorageReader(), subprocess_timeout_seconds=1)

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        raise subprocess.TimeoutExpired(args, timeout=1)

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(NonRetryableProcessingError, match="timed out"):
        processor._run_subprocess(["fpcalc", "-json", "audio.bin"])


def test_audio_missing_fpcalc_is_reported_clearly(monkeypatch):
    processor = AudioFingerprintProcessor(AudioStorageReader())

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        raise FileNotFoundError()

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(NonRetryableProcessingError, match="fpcalc executable is unavailable"):
        processor._run_subprocess(["fpcalc", "-json", "audio.bin"])


def metadata_json(duration: float = 12) -> bytes:
    return json.dumps({
        "streams": [{
            "index": 0,
            "codec_name": "mp3",
            "sample_rate": "44100",
            "channels": 2,
            "bit_rate": "128000",
            "duration": str(duration),
        }],
        "format": {
            "format_name": "mp3",
            "duration": str(duration),
            "bit_rate": "128000",
        },
    }).encode("utf-8")


def fingerprint_json(fingerprint: str = "12345ABC") -> bytes:
    return json.dumps({
        "duration": 45,
        "fingerprint": fingerprint,
    }).encode("utf-8")
