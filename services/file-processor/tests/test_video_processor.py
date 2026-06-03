import io
import json
import subprocess
import uuid
from collections.abc import AsyncIterator
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pytest
from PIL import Image

from app.embeddings.base import ImageEmbeddingInferenceClient
from app.events.models import FileProcessingRequestedEvent
from app.processors import video as video_module
from app.processors.video import VideoAnalysisProcessor, sample_timestamps
from app.storage.base import StorageObjectReader, StorageObjectReference
from app.worker.errors import NonRetryableProcessingError


class VideoStorageReader(StorageObjectReader):
    def __init__(self, chunks: list[bytes] | None = None):
        self.chunks = chunks or [b"video", b"-bytes"]
        self.references: list[StorageObjectReference] = []

    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        self.references.append(reference)
        for chunk in self.chunks:
            yield chunk


class FakeEmbeddingClient(ImageEmbeddingInferenceClient):
    def __init__(self, dimension: int = 2):
        self.calls = 0
        self.dimension = dimension

    async def embed_image(self, pixel_values: np.ndarray) -> np.ndarray:
        self.calls += 1
        assert pixel_values.shape == (1, 3, 32, 32)
        return np.ones((1, self.dimension), dtype=np.float32)


@pytest.fixture
def video_event() -> FileProcessingRequestedEvent:
    return FileProcessingRequestedEvent(
        event_id=uuid.uuid4(),
        event_type="file.processing.requested",
        occurred_at=datetime.now(timezone.utc),
        file_id=uuid.uuid4(),
        processing_job_id=uuid.uuid4(),
        job_type="VIDEO_ANALYSIS",
        storage_path="objects/video.mp4",
        mime_type="video/mp4",
        size=11,
        owner_user_id=uuid.uuid4(),
        owner_organization_id=None,
    )


def test_video_sampling_policy_caps_long_video():
    timestamps = sample_timestamps(
        duration_seconds=10_000,
        min_sampled_frames=4,
        max_sampled_frames=32,
        target_interval_seconds=10,
    )

    assert len(timestamps) == 32
    assert timestamps[0] > 0
    assert timestamps[-1] < 10_000


def test_video_sampling_policy_samples_short_video():
    timestamps = sample_timestamps(
        duration_seconds=0.25,
        min_sampled_frames=4,
        max_sampled_frames=32,
        target_interval_seconds=10,
    )

    assert len(timestamps) == 4
    assert all(0 < timestamp <= 0.25 for timestamp in timestamps)


def test_video_processor_selection_keeps_image_processors_separate(monkeypatch, video_event):
    monkeypatch.setattr(video_module.settings, "worker_video_supported_mime_types", "video/mp4,video/x-msvideo")
    processor = VideoAnalysisProcessor(
        VideoStorageReader(),
        FakeEmbeddingClient(),
        embedding_dimension=2,
        input_size=32,
        min_sampled_frames=1,
        max_sampled_frames=2,
    )

    assert processor.should_process(video_event) is True
    video_event.mime_type = "image/jpeg"
    assert processor.should_process(video_event) is False
    video_event.mime_type = "video/x-msvideo"
    assert processor.should_process(video_event) is True
    video_event.mime_type = "video/x-flv"
    assert processor.should_process(video_event) is False


def test_video_processor_selection_supports_configured_video_mime_types(monkeypatch, video_event):
    monkeypatch.setattr(
        video_module.settings,
        "worker_video_supported_mime_types",
        "video/x-msvideo,video/matroska,video/x-matroska,video/x-m4v,video/mpeg,video/MP2T,video/3gpp",
    )
    processor = VideoAnalysisProcessor(
        VideoStorageReader(),
        FakeEmbeddingClient(),
        embedding_dimension=2,
        input_size=32,
        min_sampled_frames=1,
        max_sampled_frames=2,
    )

    for mime_type in (
        "video/x-msvideo",
        "video/matroska",
        "video/x-matroska",
        "video/x-m4v",
        "video/mpeg",
        "video/MP2T",
        "video/3gpp",
        "VIDEO/X-MATROSKA",
        "video/x-matroska; charset=binary",
    ):
        video_event.mime_type = mime_type
        assert processor.should_process(video_event) is True


@pytest.mark.asyncio
async def test_video_analysis_uses_storage_reference_and_argument_lists(monkeypatch, video_event):
    storage = VideoStorageReader()
    client = FakeEmbeddingClient()
    processor = VideoAnalysisProcessor(
        storage,
        client,
        embedding_dimension=2,
        input_size=32,
        min_sampled_frames=1,
        max_sampled_frames=2,
        target_interval_seconds=10,
        max_file_bytes=1024,
        max_duration_seconds=60,
    )
    calls: list[list[str]] = []

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        calls.append(args)
        assert kwargs["shell"] is False
        assert kwargs["timeout"] == processor.frame_timeout_seconds
        assert isinstance(args, list)
        if args[0] == "ffprobe":
            return subprocess.CompletedProcess(args, 0, stdout=metadata_json(), stderr=b"")

        return subprocess.CompletedProcess(args, 0, stdout=png_bytes(), stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    result = await processor.process(video_event)

    assert storage.references == [StorageObjectReference(path="objects/video.mp4")]
    assert result["durationMs"] == 12_000
    assert result["sampledFrameCount"] == 2
    assert result["dimension"] == 2
    assert result["modelName"] == "openai/clip-vit-large-patch14"
    assert client.calls == 2
    assert calls[0][0] == "ffprobe"
    assert calls[1][0] == "ffmpeg"
    assert not Path(calls[0][-1]).exists()


@pytest.mark.asyncio
async def test_video_analysis_payload_dimension_uses_embedding_length(monkeypatch, video_event):
    processor = VideoAnalysisProcessor(
        VideoStorageReader(),
        FakeEmbeddingClient(dimension=3),
        embedding_dimension=3,
        input_size=32,
        min_sampled_frames=1,
        max_sampled_frames=1,
        max_file_bytes=1024,
        max_duration_seconds=60,
    )

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        if args[0] == "ffprobe":
            return subprocess.CompletedProcess(args, 0, stdout=metadata_json(), stderr=b"")

        return subprocess.CompletedProcess(args, 0, stdout=png_bytes(), stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    result = await processor.process(video_event)

    assert result["dimension"] == 3
    frames = result["frames"]
    assert isinstance(frames, list)
    assert len(frames[0]["embedding"]) == 3


@pytest.mark.asyncio
async def test_video_analysis_rejects_oversized_file_without_reading(video_event):
    storage = VideoStorageReader()
    processor = VideoAnalysisProcessor(
        storage,
        FakeEmbeddingClient(),
        embedding_dimension=2,
        input_size=32,
        max_file_bytes=10,
    )
    video_event.size = 11

    with pytest.raises(NonRetryableProcessingError, match="maximum processing size"):
        await processor.process(video_event)

    assert storage.references == []


@pytest.mark.asyncio
async def test_video_analysis_rejects_duration_over_limit(monkeypatch, video_event):
    processor = VideoAnalysisProcessor(
        VideoStorageReader(),
        FakeEmbeddingClient(),
        embedding_dimension=2,
        input_size=32,
        max_file_bytes=1024,
        max_duration_seconds=1,
    )

    def fake_run(args: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
        if args[0] == "ffprobe":
            return subprocess.CompletedProcess(args, 0, stdout=metadata_json(duration=12), stderr=b"")

        return subprocess.CompletedProcess(args, 0, stdout=png_bytes(), stderr=b"")

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(NonRetryableProcessingError, match="maximum processing duration"):
        await processor.process(video_event)


def metadata_json(duration: float = 12) -> bytes:
    return json.dumps({
        "streams": [{
            "codec_name": "h264",
            "width": 640,
            "height": 360,
            "nb_frames": "360",
            "duration": str(duration),
        }]
    }).encode("utf-8")


def png_bytes() -> bytes:
    image = Image.new("RGB", (64, 64), color="red")
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()
