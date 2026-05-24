import pytest
import uuid
import hashlib
import base64
import io
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

class ImageStorageReader(ObjectStorageReader):
    async def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        # Generate a real valid small PNG
        from PIL import Image
        import io
        img = Image.new('RGB', (8, 8), color='red')
        buf = io.BytesIO()
        img.save(buf, format='PNG')
        yield buf.getvalue()

class FakeResultSink(ProcessingResultSink):
    def __init__(self):
        self.checksum_reported = False
        self.phash_reported = False
        self.failure_reported = False

    async def report_checksum_success(self, job_id: uuid.UUID, file_id: uuid.UUID, sha256: str):
        self.checksum_reported = True
        self.reported_sha256 = sha256

    async def report_phash_success(self, job_id: uuid.UUID, file_id: uuid.UUID, phash: str):
        self.phash_reported = True
        self.reported_phash = phash

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
        job_type="CHECKSUM",
        storage_path="test.jpg",
        mime_type="image/jpeg",
        size=500,
        owner_user_id=uuid.uuid4(),
        owner_organization_id=None
    )

def test_processor_selection(sample_event):
    storage = FakeStorageReader()
    checksum = ChecksumProcessor(storage)
    phash = PHashProcessor(storage)
    
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is True
    
    sample_event.mime_type = "text/plain"
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is False

    # Test uppercase MIME type
    sample_event.mime_type = "IMAGE/PNG"
    assert phash.should_process(sample_event) is True

    # Test empty MIME type
    sample_event.mime_type = ""
    assert phash.should_process(sample_event) is False

@pytest.mark.asyncio
async def test_phash_processor_real_hash(sample_event):
    storage = ImageStorageReader()
    phash_processor = PHashProcessor(storage)
    
    result = await phash_processor.process(sample_event)
    
    assert "phash" in result
    assert len(result["phash"]) == 16
    assert result["phash"] == result["phash"].lower()
    # Check if it's valid hex
    int(result["phash"], 16)

@pytest.mark.asyncio
async def test_processing_flow(sample_event):
    storage = FakeStorageReader()
    sink = FakeResultSink()
    checksum = ChecksumProcessor(storage)
    flow = ProcessingFlow(processors=[checksum], result_sink=sink)
    
    derived_data = await flow.run(sample_event)
    
    expected_hash = hashlib.sha256(b"test data").hexdigest()
    assert derived_data["sha256"] == expected_hash
    assert sink.checksum_reported is True
    assert sink.reported_sha256 == expected_hash
