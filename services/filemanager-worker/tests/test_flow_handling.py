import pytest
import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from app.events.models import FileProcessingRequestedEvent
from app.worker.flow import ProcessingFlow
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink

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
    processor.process = AsyncMock(return_value={"sha256": "fake-hash"})
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act
    derived_data = await flow.run(sample_event)
    
    # Assert
    assert derived_data == {"sha256": "fake-hash"}
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
    processor.process = AsyncMock(return_value={"phash": "fake-phash"})
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act
    derived_data = await flow.run(sample_event)
    
    # Assert
    assert derived_data == {"phash": "fake-phash"}
    sink.report_phash_success.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        "fake-phash"
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
    
    # Act
    derived_data = await flow.run(sample_event)
    
    # Assert
    # If handled, it returns empty result instead of raising
    assert derived_data == {}
    sink.report_failure.assert_called_once_with(
        sample_event.processing_job_id,
        sample_event.file_id,
        "Processing failed"
    )

@pytest.mark.asyncio
async def test_flow_report_failure_fails_propagates(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock(side_effect=Exception("API unreachable"))
    
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(side_effect=Exception("Processing failed"))
    
    flow = ProcessingFlow(processors=[processor], result_sink=sink)
    
    # Act & Assert
    with pytest.raises(Exception, match="API unreachable"):
        await flow.run(sample_event)
    
    sink.report_failure.assert_called_once()

@pytest.mark.asyncio
async def test_flow_unsupported_job_type_reports_failure(sample_event):
    # Arrange
    sample_event.job_type = "EMBEDDING"
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock()
    
    # No embedding processor registered
    flow = ProcessingFlow(processors=[], result_sink=sink)
    
    # Act
    await flow.run(sample_event)
    
    # Assert
    sink.report_failure.assert_called_once()
    assert "No processor found" in sink.report_failure.call_args[0][2]

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
    
    # Act
    await flow.run(sample_event)
    
    # Assert
    sink.report_failure.assert_called_once()
    assert "did not produce required 'sha256'" in sink.report_failure.call_args[0][2]
