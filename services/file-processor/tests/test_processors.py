import hashlib
import io
import uuid
from datetime import datetime, timezone
from typing import AsyncIterator

import numpy as np
import pytest
from PIL import Image

from app.embeddings.base import ImageEmbeddingInferenceClient, ImageEmbeddingServiceUnavailable
from app.events.models import FileProcessingRequestedEvent
from app.processors.embedding import ImageEmbeddingProcessor, preprocess_clip_image
from app.processors.impl import ChecksumProcessor, PHashProcessor
from app.sinks.base import ProcessingResultSink
from app.storage.base import ObjectStorageReader
from app.worker.flow import ProcessingFlow
from app.worker.errors import NonRetryableProcessingError


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

class OversizedImageStorageReader(ObjectStorageReader):
    async def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        yield b"a" * 6
        yield b"b" * 6

class CorruptImageStorageReader(ObjectStorageReader):
    async def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        yield b"not an image"

class FakeResultSink(ProcessingResultSink):
    def __init__(self):
        self.checksum_reported = False
        self.reported_sha256 = None
        self.phash_reported = False
        self.reported_phash = None
        self.failure_reported = False

    async def report_checksum_success(self, job_id: uuid.UUID, file_id: uuid.UUID, sha256: str):
        self.checksum_reported = True
        self.reported_sha256 = sha256

    async def report_phash_success(self, job_id: uuid.UUID, file_id: uuid.UUID, phash: str):
        self.phash_reported = True
        self.reported_phash = phash

    async def report_embedding_success(
        self,
        job_id: uuid.UUID,
        file_id: uuid.UUID,
        model_name: str,
        model_version: str,
        dimension: int,
        embedding,
    ):
        pass

    async def report_failure(self, job_id: uuid.UUID, file_id: uuid.UUID, error_message: str):
        self.failure_reported = True


class FakeEmbeddingClient(ImageEmbeddingInferenceClient):
    def __init__(self, output: np.ndarray | None = None, error: Exception | None = None):
        self.output = output if output is not None else np.ones((1, 768), dtype=np.float32)
        self.error = error
        self.last_pixel_values: np.ndarray | None = None

    async def embed_image(self, pixel_values: np.ndarray) -> np.ndarray:
        self.last_pixel_values = pixel_values
        if self.error:
            raise self.error
        return self.output

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
    embedding = ImageEmbeddingProcessor(storage, FakeEmbeddingClient())
    
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is True
    sample_event.job_type = "EMBEDDING"
    assert embedding.should_process(sample_event) is True
    
    sample_event.mime_type = "text/plain"
    assert checksum.should_process(sample_event) is True
    assert phash.should_process(sample_event) is False
    assert embedding.should_process(sample_event) is False

    # Test uppercase MIME type
    sample_event.mime_type = "IMAGE/PNG"
    sample_event.job_type = "PHASH"
    assert phash.should_process(sample_event) is True
    sample_event.job_type = "EMBEDDING"
    assert embedding.should_process(sample_event) is True

    # Test empty MIME type
    sample_event.mime_type = ""
    assert phash.should_process(sample_event) is False
    assert embedding.should_process(sample_event) is False

    sample_event.mime_type = "image/jpeg"
    sample_event.job_type = "PHASH"
    assert embedding.should_process(sample_event) is False

@pytest.mark.asyncio
async def test_phash_processor_real_hash(sample_event):
    storage = ImageStorageReader()
    phash_processor = PHashProcessor(storage, max_image_bytes=1024 * 1024)
    
    result = await phash_processor.process(sample_event)
    
    assert "phash" in result
    assert len(result["phash"]) == 16
    assert result["phash"] == result["phash"].lower()
    # Check if it's a valid hex
    int(result["phash"], 16)

@pytest.mark.asyncio
async def test_phash_processor_rejects_oversized_image(sample_event):
    storage = OversizedImageStorageReader()
    phash_processor = PHashProcessor(storage, max_image_bytes=10)

    with pytest.raises(NonRetryableProcessingError, match="maximum pHash processing size"):
        await phash_processor.process(sample_event)

@pytest.mark.asyncio
async def test_phash_processor_rejects_corrupt_image(sample_event):
    storage = CorruptImageStorageReader()
    phash_processor = PHashProcessor(storage, max_image_bytes=1024)

    with pytest.raises(NonRetryableProcessingError, match="Corrupt or unsupported image"):
        await phash_processor.process(sample_event)


def test_embedding_preprocessing_shape_and_dtype():
    image = ImageStorageReader()
    data = b""

    async def collect() -> bytes:
        nonlocal data
        async for chunk in image.read_object("test"):
            data += chunk
        return data

    import asyncio

    raw = asyncio.run(collect())
    with Image.open(io.BytesIO(raw)) as img:
        pixel_values = preprocess_clip_image(img, 224)

    assert pixel_values.shape == (1, 3, 224, 224)
    assert pixel_values.dtype == np.float32
    assert pixel_values.flags["C_CONTIGUOUS"]


@pytest.mark.asyncio
async def test_embedding_processor_valid_response(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = ImageStorageReader()
    client = FakeEmbeddingClient(output=np.ones((1, 768), dtype=np.float32))
    processor = ImageEmbeddingProcessor(storage, client, max_image_bytes=1024 * 1024)

    result = await processor.process(sample_event)

    assert result["modelName"] == "openai/clip-vit-large-patch14"
    assert result["modelVersion"] == "1"
    assert result["dimension"] == 768
    assert len(result["embedding"]) == 768
    assert np.isclose(np.linalg.norm(np.array(result["embedding"], dtype=np.float32)), 1.0)
    assert client.last_pixel_values is not None
    assert client.last_pixel_values.shape == (1, 3, 224, 224)


@pytest.mark.asyncio
async def test_embedding_processor_rejects_wrong_output_dimension(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = ImageStorageReader()
    client = FakeEmbeddingClient(output=np.ones((1, 512), dtype=np.float32))
    processor = ImageEmbeddingProcessor(storage, client, max_image_bytes=1024 * 1024)

    with pytest.raises(NonRetryableProcessingError, match="dimension mismatch"):
        await processor.process(sample_event)


@pytest.mark.asyncio
async def test_embedding_processor_rejects_corrupt_image(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = CorruptImageStorageReader()
    processor = ImageEmbeddingProcessor(storage, FakeEmbeddingClient(), max_image_bytes=1024)

    with pytest.raises(NonRetryableProcessingError, match="Corrupt or unsupported image"):
        await processor.process(sample_event)


@pytest.mark.asyncio
async def test_embedding_processor_rejects_oversized_image(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = OversizedImageStorageReader()
    processor = ImageEmbeddingProcessor(storage, FakeEmbeddingClient(), max_image_bytes=10)

    with pytest.raises(NonRetryableProcessingError, match="maximum embedding processing size"):
        await processor.process(sample_event)


@pytest.mark.asyncio
async def test_embedding_processor_triton_failure_is_retryable(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = ImageStorageReader()
    client = FakeEmbeddingClient(error=ImageEmbeddingServiceUnavailable("down"))
    processor = ImageEmbeddingProcessor(storage, client, max_image_bytes=1024 * 1024)

    from app.worker.errors import RetryableProcessingError

    with pytest.raises(RetryableProcessingError, match="Triton image embedding inference unavailable"):
        await processor.process(sample_event)

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
