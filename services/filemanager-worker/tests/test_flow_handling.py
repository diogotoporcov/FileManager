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
    
    flow = ProcessingFlow(processors=[processor], matchers=[], result_sink=sink)
    
    # Act
    derived_data, matches = await flow.run(sample_event)
    
    # Assert
    assert derived_data == {"sha256": "fake-hash"}
    sink.report_checksum_success.assert_called_once()
    sink.report_failure.assert_not_called()

@pytest.mark.asyncio
async def test_flow_processor_failure_handled(sample_event):
    # Arrange
    sink = MagicMock(spec=ProcessingResultSink)
    sink.report_failure = AsyncMock()
    
    processor = MagicMock(spec=Processor)
    processor.name = "checksum"
    processor.should_process.return_value = True
    processor.process = AsyncMock(side_effect=Exception("Processing failed"))
    
    flow = ProcessingFlow(processors=[processor], matchers=[], result_sink=sink)
    
    # Act
    derived_data, matches = await flow.run(sample_event)
    
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
    
    flow = ProcessingFlow(processors=[processor], matchers=[], result_sink=sink)
    
    # Act & Assert
    with pytest.raises(Exception, match="API unreachable"):
        await flow.run(sample_event)
    
    sink.report_failure.assert_called_once()
