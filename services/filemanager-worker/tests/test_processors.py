import pytest
import uuid
import hashlib
from datetime import datetime, timezone
from typing import AsyncIterator
from app.events.models import FileProcessingRequestedEvent
from app.processors.impl import ChecksumProcessor, PHashProcessor
from app.worker.flow import ProcessingFlow
from app.storage.base import ObjectStorageReader
from app.sinks.base import ProcessingResultSink

class FakeStorageReader(ObjectStorageReader):
    async def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        yield b"test data"

class FakeResultSink(ProcessingResultSink):
    def __init__(self):
        self.checksum_reported = False
        self.failure_reported = False

    async def report_checksum_success(self, job_id: uuid.UUID, file_id: uuid.UUID, sha256: str):
        self.checksum_reported = True
        self.reported_sha256 = sha256

    async def report_failure(self, job_id: uuid.UUID, file_id: uuid.UUID, error_message: str):
        self.failure_reported = True

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
    storage = FakeStorageReader()
    checksum = ChecksumProcessor(storage)
    phash = PHashProcessor()
    
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is True
    
    sample_event.mime_type = "text/plain"
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is False

@pytest.mark.asyncio
async def test_checksum_processor_real_hash(sample_event):
    storage = FakeStorageReader()
    checksum = ChecksumProcessor(storage)
    
    result = await checksum.process(sample_event)
    
    expected_hash = hashlib.sha256(b"test data").hexdigest()
    assert result["sha256"] == expected_hash

@pytest.mark.asyncio
async def test_processing_flow(sample_event):
    storage = FakeStorageReader()
    sink = FakeResultSink()
    checksum = ChecksumProcessor(storage)
    flow = ProcessingFlow(processors=[checksum], matchers=[], result_sink=sink)
    
    derived_data, matches = await flow.run(sample_event)
    
    expected_hash = hashlib.sha256(b"test data").hexdigest()
    assert derived_data["sha256"] == expected_hash
    assert sink.checksum_reported is True
    assert sink.reported_sha256 == expected_hash
    assert len(matches) == 0
