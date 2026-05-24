import pytest
import uuid
from datetime import datetime, timezone
from app.events.models import FileProcessingRequestedEvent
from app.processors.impl import ChecksumProcessor, PHashProcessor
from app.worker.flow import ProcessingFlow

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

def test_processor_selection(sample_event):
    checksum = ChecksumProcessor()
    phash = PHashProcessor()
    
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is True
    
    sample_event.mime_type = "text/plain"
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is False

@pytest.mark.asyncio
async def test_processing_flow(sample_event):
    checksum = ChecksumProcessor()
    flow = ProcessingFlow(processors=[checksum], matchers=[])
    
    derived_data, matches = await flow.run(sample_event)
    
    assert "sha256" not in derived_data
    assert len(derived_data) == 0
    assert len(matches) == 0
