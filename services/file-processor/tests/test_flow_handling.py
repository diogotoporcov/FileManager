import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink
from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError
from app.worker.flow import ProcessingFlow

VALID_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
VALID_PHASH = "1234567890abcdef"
VALID_EMBEDDING = [0.5, 0.5]


@pytest.fixture
def sample_event():
    return FileProcessingRequestedEvent(
        event_id=uuid.uuid4(),
        event_type="file.processing.requested",
        occurred_at=datetime.now(timezone.utc),
        file_id=uuid.uuid4(),
        processing_job_id=uuid.uuid4(),
        job_type="CHECKSUM",
        storage_path="test.jpg",
        mime_type="image/jpeg",
        size=500,
        owner_user_id=uuid.uuid4(),
    )


@pytest.mark.asyncio
async def test_flow_checksum_success(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_checksum_success = AsyncMock()
    proc = make_processor("checksum", {"sha256": VALID_SHA256})

    result = await ProcessingFlow([proc], sink).run(sample_event)

    assert result == {"sha256": VALID_SHA256}
    sink.report_checksum_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        VALID_SHA256,
    )


@pytest.mark.asyncio
async def test_flow_phash_success(sample_event):
    sample_event.job_type = "PHASH"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_phash_success = AsyncMock()

    await ProcessingFlow([make_processor("phash", {"phash": VALID_PHASH})], sink).run(sample_event)

    sink.report_phash_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        VALID_PHASH,
    )


@pytest.mark.asyncio
async def test_flow_embedding_success(sample_event):
    sample_event.job_type = "EMBEDDING"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_embedding_success = AsyncMock()
    result = {
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 2,
        "embedding": VALID_EMBEDDING,
    }

    await ProcessingFlow([make_processor("embedding", result)], sink).run(sample_event)

    sink.report_embedding_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        "openai/clip-vit-large-patch14",
        "1",
        2,
        VALID_EMBEDDING,
    )


@pytest.mark.asyncio
async def test_flow_audio_analysis_success(sample_event):
    sample_event.job_type = "AUDIO_ANALYSIS"
    sample_event.mime_type = "audio/mpeg"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_audio_analysis_success = AsyncMock()
    result = {
        "durationMs": 1000,
        "codec": "mp3",
        "sampleRate": 44100,
        "channels": 2,
        "fingerprint": "12345ABC",
        "fingerprintAlgorithm": "chromaprint",
        "fingerprintVersion": "fpcalc",
        "fingerprintDurationSeconds": 60,
    }

    await ProcessingFlow([make_processor("audio_analysis", result)], sink).run(sample_event)

    sink.report_audio_analysis_success.assert_called_once()


@pytest.mark.asyncio
async def test_flow_rejects_video_analysis(sample_event):
    sample_event.job_type = "VIDEO_ANALYSIS"
    sample_event.mime_type = "video/mp4"
    sink = MagicMock(spec=ProcessingResultSink)

    with pytest.raises(NonRetryableProcessingError, match="No processor found"):
        await ProcessingFlow([], sink).run(sample_event)


@pytest.mark.asyncio
async def test_flow_rejects_audio_analysis_for_video_mime(sample_event):
    sample_event.job_type = "AUDIO_ANALYSIS"
    sample_event.mime_type = "video/mp4"
    sink = MagicMock(spec=ProcessingResultSink)
    proc = make_processor("audio_analysis", {})

    with pytest.raises(NonRetryableProcessingError, match="audio analysis only supports audio MIME types"):
        await ProcessingFlow([proc], sink).run(sample_event)

    proc.process.assert_not_called()


@pytest.mark.asyncio
async def test_flow_report_failure_propagates_sink_failure(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock(side_effect=Exception("API unreachable"))

    with pytest.raises(RetryableProcessingError):
        await ProcessingFlow([], sink).report_failure(sample_event, "Test error")


@pytest.mark.asyncio
async def test_flow_invalid_processor_result_type_fails(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    proc = make_processor("checksum", None)

    with pytest.raises(NonRetryableProcessingError, match="returned invalid result type: NoneType"):
        await ProcessingFlow([proc], sink).run(sample_event)


def make_processor(name: str, result):
    proc = MagicMock(spec=Processor)
    proc.name = name
    proc.should_process.return_value = True
    proc.process = AsyncMock(return_value=result)
    return proc
