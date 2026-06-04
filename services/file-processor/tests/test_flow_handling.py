import pytest
import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

from app.events.models import FileProcessingRequestedEvent
from app.worker.flow import ProcessingFlow
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink

from app.worker.errors import NonRetryableProcessingError

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
        owner_organization_id=None
    )

@pytest.mark.asyncio
async def test_flow_success_handled(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_checksum_success = AsyncMock()
    sink.report_failure = AsyncMock()
    
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={"sha256": VALID_SHA256})
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act
    derived_data = await flow.run(sample_event)
    
    # Assert
    assert derived_data == {"sha256": VALID_SHA256}
    sink.report_checksum_success.assert_called_once()
    sink.report_failure.assert_not_called()

@pytest.mark.asyncio
async def test_flow_phash_success(sample_event):
    # Arrange
    sample_event.job_type = "PHASH"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_phash_success = AsyncMock()
    
    processor = MagicMock(spec=Processor)
    processor.name = "phash"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={"phash": VALID_PHASH})
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act
    derived_data = await flow.run(sample_event)
    
    # Assert
    assert derived_data == {"phash": VALID_PHASH}
    sink.report_phash_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        VALID_PHASH
    )

@pytest.mark.asyncio
async def test_flow_embedding_success(sample_event):
    sample_event.job_type = "EMBEDDING"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_embedding_success = AsyncMock()

    processor = MagicMock(spec=Processor)
    processor.name = "embedding"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 2,
        "embedding": VALID_EMBEDDING,
    })

    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    derived_data = await flow.run(sample_event)

    assert derived_data["dimension"] == 2
    sink.report_embedding_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        "openai/clip-vit-large-patch14",
        "1",
        2,
        VALID_EMBEDDING,
    )

@pytest.mark.asyncio
async def test_flow_video_analysis_success(sample_event):
    sample_event.job_type = "VIDEO_ANALYSIS"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_video_analysis_success = AsyncMock()

    result = {
        "durationMs": 1000,
        "width": 320,
        "height": 240,
        "frameCount": 30,
        "codec": "h264",
        "sampledFrameCount": 1,
        "samplingStrategy": "even_interval:min=1,max=32,target_seconds=10",
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 2,
        "frames": [{
            "timestampMs": 500,
            "frameIndex": 0,
            "phash": VALID_PHASH,
            "embedding": VALID_EMBEDDING,
        }],
    }
    processor = MagicMock(spec=Processor)
    processor.name = "video_analysis"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value=result)

    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    derived_data = await flow.run(sample_event)

    assert derived_data["sampledFrameCount"] == 1
    sink.report_video_analysis_success.assert_called_once()


@pytest.mark.asyncio
async def test_flow_video_analysis_accepts_phash_only_frames(sample_event):
    sample_event.job_type = "VIDEO_ANALYSIS"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_video_analysis_success = AsyncMock()
    result = {
        "durationMs": 1000,
        "sampledFrameCount": 1,
        "samplingStrategy": "even_interval:min=1,max=32,target_seconds=10",
        "frames": [{
            "timestampMs": 500,
            "frameIndex": 0,
            "phash": VALID_PHASH,
        }],
    }
    processor = MagicMock(spec=Processor)
    processor.name = "video_analysis"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value=result)
    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    derived_data = await flow.run(sample_event)

    assert "modelName" not in derived_data
    assert "embedding" not in derived_data["frames"][0]
    sink.report_video_analysis_success.assert_called_once()


@pytest.mark.asyncio
async def test_flow_video_analysis_accepts_embedding_only_frames(sample_event):
    sample_event.job_type = "VIDEO_ANALYSIS"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_video_analysis_success = AsyncMock()
    result = {
        "durationMs": 1000,
        "sampledFrameCount": 1,
        "samplingStrategy": "even_interval:min=1,max=32,target_seconds=10",
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 2,
        "frames": [{
            "timestampMs": 500,
            "frameIndex": 0,
            "embedding": VALID_EMBEDDING,
        }],
    }
    processor = MagicMock(spec=Processor)
    processor.name = "video_analysis"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value=result)
    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    derived_data = await flow.run(sample_event)

    assert "phash" not in derived_data["frames"][0]
    assert derived_data["dimension"] == 2
    sink.report_video_analysis_success.assert_called_once()

@pytest.mark.asyncio
async def test_flow_audio_analysis_success(sample_event):
    sample_event.job_type = "AUDIO_ANALYSIS"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_audio_analysis_success = AsyncMock()

    result = {
        "durationMs": 1000,
        "codec": "mp3",
        "sampleRate": 44100,
        "channels": 2,
        "bitRate": 128000,
        "audioStreamIndex": 0,
        "containerFormat": "mp3",
        "fingerprint": "12345ABC",
        "fingerprintAlgorithm": "chromaprint",
        "fingerprintVersion": "fpcalc",
        "fingerprintDurationSeconds": 60,
    }
    processor = MagicMock(spec=Processor)
    processor.name = "audio_analysis"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value=result)

    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    derived_data = await flow.run(sample_event)

    assert derived_data["fingerprintAlgorithm"] == "chromaprint"
    sink.report_audio_analysis_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        result,
    )

@pytest.mark.asyncio
async def test_flow_processor_failure_handled(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock()
    
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(side_effect=Exception("Processing failed"))
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act & Assert
    with pytest.raises(Exception, match="Processing failed"):
        await flow.run(sample_event)
    
    sink.report_failure.assert_not_called()

@pytest.mark.asyncio
async def test_flow_report_failure_works(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock()
    
    flow = ProcessingFlow(processors=[], result_sink=sink)
    
    # Act
    await flow.report_failure(sample_event, "Test error")
    
    # Assert
    sink.report_failure.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        "Test error"
    )

@pytest.mark.asyncio
async def test_flow_report_failure_fails_propagates(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock(side_effect=Exception("API unreachable"))
    
    flow = ProcessingFlow(processors=[], result_sink=sink)
    
    # Act & Assert
    from app.worker.errors import RetryableProcessingError
    with pytest.raises(RetryableProcessingError):
        await flow.report_failure(sample_event, "Test error")

@pytest.mark.asyncio
async def test_flow_unsupported_job_type_reports_failure(sample_event):
    # Arrange
    sample_event.job_type = "EMBEDDING"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock()
    
    # No embedding processor registered
    flow = ProcessingFlow(processors=[], result_sink=sink)
    
    # Act & Assert
    with pytest.raises(NonRetryableProcessingError):
        await flow.run(sample_event)
    
    # Assert
    sink.report_failure.assert_not_called()


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("job_type", "processor_name", "mime_type", "setting_name"),
    [
        ("CHECKSUM", "checksum", "application/pdf", "worker_checksum_enabled"),
        ("PHASH", "phash", "image/png", "worker_image_phash_enabled"),
        ("EMBEDDING", "embedding", "image/png", "worker_image_embedding_enabled"),
        ("VIDEO_ANALYSIS", "video_analysis", "video/mp4", "worker_video_analysis_enabled"),
        ("AUDIO_ANALYSIS", "audio_analysis", "audio/mpeg", "worker_audio_fingerprint_enabled"),
    ],
)
async def test_flow_disabled_capability_is_non_retryable_without_processor_call(
    monkeypatch,
    sample_event,
    job_type,
    processor_name,
    mime_type,
    setting_name,
):
    monkeypatch.setattr("app.worker.policy.settings." + setting_name, False)
    sample_event.job_type = job_type
    sample_event.mime_type = mime_type
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = processor_name
    processor.should_process.return_value = True
    processor.process = AsyncMock()

    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    with pytest.raises(NonRetryableProcessingError, match="Processing capability disabled"):
        await flow.run(sample_event)

    processor.process.assert_not_called()


@pytest.mark.asyncio
async def test_flow_video_audio_toggle_is_independent_from_standalone_audio(monkeypatch, sample_event):
    monkeypatch.setattr("app.worker.policy.settings.worker_video_audio_analysis_enabled", False)
    monkeypatch.setattr("app.worker.policy.settings.worker_audio_fingerprint_enabled", True)
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "audio_analysis"
    processor.should_process.return_value = True
    processor.process = AsyncMock()
    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    sample_event.job_type = "AUDIO_ANALYSIS"
    sample_event.mime_type = "video/mp4"
    with pytest.raises(NonRetryableProcessingError, match="video audio-track analysis is disabled"):
        await flow.run(sample_event)
    processor.process.assert_not_called()

    sample_event.mime_type = "audio/mpeg"
    processor.process = AsyncMock(return_value={
        "durationMs": 1000,
        "codec": "mp3",
        "sampleRate": 44100,
        "channels": 2,
        "fingerprint": "12345ABC",
        "fingerprintAlgorithm": "chromaprint",
        "fingerprintVersion": "fpcalc",
        "fingerprintDurationSeconds": 60,
    })
    sink.report_audio_analysis_success = AsyncMock()

    await flow.run(sample_event)

    processor.process.assert_called_once()


@pytest.mark.asyncio
async def test_flow_video_frame_outputs_disabled_is_non_retryable_noop(monkeypatch, sample_event):
    monkeypatch.setattr("app.worker.policy.settings.worker_video_analysis_enabled", True)
    monkeypatch.setattr("app.worker.policy.settings.worker_video_frame_phash_enabled", False)
    monkeypatch.setattr("app.worker.policy.settings.worker_video_frame_embedding_enabled", False)
    sample_event.job_type = "VIDEO_ANALYSIS"
    sample_event.mime_type = "video/mp4"
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "video_analysis"
    processor.should_process.return_value = True
    processor.process = AsyncMock()
    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    with pytest.raises(NonRetryableProcessingError, match="no enabled frame outputs"):
        await flow.run(sample_event)

    processor.process.assert_not_called()

@pytest.mark.asyncio
async def test_flow_embedding_missing_required_output_fails(sample_event):
    sample_event.job_type = "EMBEDDING"
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "embedding"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 2,
    })

    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    with pytest.raises(NonRetryableProcessingError, match="required embedding output"):
        await flow.run(sample_event)

@pytest.mark.asyncio
async def test_flow_embedding_invalid_dimension_fails_without_vector_in_error(sample_event):
    sample_event.job_type = "EMBEDDING"
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "embedding"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 3,
        "embedding": [123.456, 789.012],
    })

    flow = ProcessingFlow(processors=[processor], result_sink=sink)

    with pytest.raises(NonRetryableProcessingError) as exc_info:
        await flow.run(sample_event)

    assert "123.456" not in str(exc_info.value)
    assert "789.012" not in str(exc_info.value)

@pytest.mark.asyncio
async def test_flow_missing_required_output_reports_failure(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock()
    
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={}) # Missing 'sha256'
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act & Assert
    with pytest.raises(NonRetryableProcessingError):
        await flow.run(sample_event)
    
    # Assert
    sink.report_failure.assert_not_called()

@pytest.mark.asyncio
async def test_flow_processor_returns_none_fails(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value=None)
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    with pytest.raises(NonRetryableProcessingError, match="returned invalid result type: NoneType"):
        await flow.run(sample_event)

@pytest.mark.asyncio
async def test_flow_processor_returns_list_fails(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value=["item1"])
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    with pytest.raises(NonRetryableProcessingError, match="returned invalid result type: list"):
        await flow.run(sample_event)

@pytest.mark.asyncio
async def test_flow_processor_returns_string_fails(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value="not-a-dict")
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    with pytest.raises(NonRetryableProcessingError, match="returned invalid result type: str"):
        await flow.run(sample_event)

@pytest.mark.asyncio
async def test_flow_phash_missing_required_output_fails(sample_event):
    sample_event.job_type = "PHASH"
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "phash"
    processor.should_process.return_value = True
    processor.process = AsyncMock(return_value={}) # Missing 'phash'
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    with pytest.raises(NonRetryableProcessingError, match="did not produce required 'phash' output"):
        await flow.run(sample_event)

@pytest.mark.asyncio
async def test_flow_checksum_invalid_value_fails(sample_event):
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # None value
    processor.process = AsyncMock(return_value={"sha256": None})
    with pytest.raises(NonRetryableProcessingError, match="produced invalid 'sha256' format"):
        await flow.run(sample_event)
        
    # Too short
    processor.process = AsyncMock(return_value={"sha256": "bad"})
    with pytest.raises(NonRetryableProcessingError, match="produced invalid 'sha256' format"):
        await flow.run(sample_event)

@pytest.mark.asyncio
async def test_flow_phash_invalid_value_fails(sample_event):
    sample_event.job_type = "PHASH"
    sink = MagicMock(spec=ProcessingResultSink)
    processor = MagicMock(spec=Processor)
    processor.name = "phash"
    processor.should_process.return_value = True
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # None value
    processor.process = AsyncMock(return_value={"phash": None})
    with pytest.raises(NonRetryableProcessingError, match="produced invalid 'phash' format"):
        await flow.run(sample_event)
        
    # Too short
    processor.process = AsyncMock(return_value={"phash": "bad"})
    with pytest.raises(NonRetryableProcessingError, match="produced invalid 'phash' format"):
        await flow.run(sample_event)
