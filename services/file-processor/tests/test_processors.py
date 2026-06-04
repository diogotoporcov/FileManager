import hashlib
import io
import uuid
from collections.abc import AsyncIterator, Sequence
from datetime import datetime, timezone

import numpy as np
import pytest
from PIL import Image

from app.embeddings.base import ImageEmbeddingInferenceClient, ImageEmbeddingServiceUnavailable
from app.events.models import FileProcessingRequestedEvent
from app.processors.embedding import ImageEmbeddingProcessor, preprocess_clip_image
from app.processors.impl import ChecksumProcessor, PHashProcessor
from app.sinks.base import ProcessingResultSink
from app.storage.base import StorageObjectReader, StorageObjectReference
from app.worker.flow import ProcessingFlow
from app.worker.errors import NonRetryableProcessingError


class FakeStorageReader(StorageObjectReader):
    def __init__(self):
        self.reads = 0

    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        self.reads += 1
        yield b"test data"


class ImageStorageReader(StorageObjectReader):
    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        img = Image.new("RGB", (8, 8), color="red")
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        yield buf.getvalue()


class OversizedImageStorageReader(StorageObjectReader):
    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        yield b"a" * 6
        yield b"b" * 6


class CorruptImageStorageReader(StorageObjectReader):
    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        yield b"not an image"


class PillowImageStorageReader(StorageObjectReader):
    def __init__(self, image_format: str):
        self.image_format = image_format

    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        img = Image.new("RGB", (64, 48), color="red")
        buf = io.BytesIO()
        img.save(buf, format=self.image_format)
        yield buf.getvalue()


class LargeJpegStorageReader(StorageObjectReader):
    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        img = Image.new("RGB", (512, 512), color="red")
        buf = io.BytesIO()
        img.save(buf, format="JPEG")
        yield buf.getvalue()


class LargeBmpStorageReader(StorageObjectReader):
    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        img = Image.new("RGB", (128, 128), color="red")
        buf = io.BytesIO()
        img.save(buf, format="BMP")
        yield buf.getvalue()


class FakeResultSink(ProcessingResultSink):
    def __init__(self):
        self.checksum_reported = False
        self.reported_sha256: str | None = None
        self.phash_reported = False
        self.reported_phash: str | None = None
        self.audio_reported = False
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
        embedding: Sequence[float],
    ):
        pass

    async def report_video_analysis_success(self, job_id: uuid.UUID, file_id: uuid.UUID, result):
        pass

    async def report_audio_analysis_success(self, job_id: uuid.UUID, file_id: uuid.UUID, result):
        self.audio_reported = True

    async def report_failure(self, job_id: uuid.UUID, file_id: uuid.UUID, error_message: str):
        self.failure_reported = True


class FakeEmbeddingClient(ImageEmbeddingInferenceClient):
    def __init__(self, output: np.ndarray | None = None, error: Exception | None = None):
        self.output = output if output is not None else np.ones((1, 768), dtype=np.float32)
        self.error = error
        self.last_pixel_values: np.ndarray | None = None
        self.calls = 0

    async def embed_image(self, pixel_values: np.ndarray) -> np.ndarray:
        self.calls += 1
        self.last_pixel_values = pixel_values

        if self.error:
            raise self.error

        return self.output


@pytest.fixture
def sample_event() -> FileProcessingRequestedEvent:
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
        owner_organization_id=None,
    )


def test_processor_selection(sample_event: FileProcessingRequestedEvent):
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

    sample_event.mime_type = "image/jpeg; charset=binary"
    sample_event.job_type = "PHASH"
    assert phash.should_process(sample_event) is True
    sample_event.job_type = "EMBEDDING"
    assert embedding.should_process(sample_event) is True

    sample_event.mime_type = "image/x-icon"
    sample_event.job_type = "PHASH"
    assert phash.should_process(sample_event) is True
    sample_event.job_type = "EMBEDDING"
    assert embedding.should_process(sample_event) is True

    # Test empty MIME type
    sample_event.mime_type = ""
    assert phash.should_process(sample_event) is False
    assert embedding.should_process(sample_event) is False

    sample_event.mime_type = "image/svg+xml"
    sample_event.job_type = "PHASH"
    assert phash.should_process(sample_event) is False
    sample_event.job_type = "EMBEDDING"
    assert embedding.should_process(sample_event) is False

    sample_event.mime_type = "image/jpeg"
    sample_event.job_type = "PHASH"
    assert embedding.should_process(sample_event) is False

@pytest.mark.asyncio
async def test_phash_processor_real_hash(sample_event):
    storage = ImageStorageReader()
    phash_processor = PHashProcessor(storage, max_image_bytes=1024 * 1024)
    
    result = await phash_processor.process(sample_event)

    phash = result["phash"]
    assert isinstance(phash, str)
    assert len(phash) == 16
    assert phash == phash.lower()
    # Check if it's a valid hex
    int(phash, 16)


@pytest.mark.asyncio
async def test_checksum_disabled_rejects_without_hashing(monkeypatch, sample_event):
    monkeypatch.setattr("app.processors.impl.settings.worker_checksum_enabled", False)
    storage = FakeStorageReader()
    processor = ChecksumProcessor(storage)

    assert processor.should_process(sample_event) is False
    with pytest.raises(NonRetryableProcessingError, match="Checksum processing is disabled"):
        await processor.process(sample_event)

    assert storage.reads == 0


@pytest.mark.asyncio
async def test_phash_disabled_rejects_without_reading(monkeypatch, sample_event):
    monkeypatch.setattr("app.processors.impl.settings.worker_image_phash_enabled", False)
    storage = FakeStorageReader()
    processor = PHashProcessor(storage)

    assert processor.should_process(sample_event) is False
    with pytest.raises(NonRetryableProcessingError, match="Image pHash processing is disabled"):
        await processor.process(sample_event)

    assert storage.reads == 0

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
        async for chunk in image.read_content(StorageObjectReference(path="test")):
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
    embedding = result["embedding"]
    assert isinstance(embedding, list)
    assert len(embedding) == 768
    assert np.isclose(np.linalg.norm(np.array(embedding, dtype=np.float32)), 1.0)
    assert client.last_pixel_values is not None
    assert client.last_pixel_values.shape == (1, 3, 224, 224)


@pytest.mark.asyncio
async def test_embedding_disabled_rejects_without_triton(monkeypatch, sample_event):
    monkeypatch.setattr("app.processors.embedding.settings.worker_image_embedding_enabled", False)
    sample_event.job_type = "EMBEDDING"
    storage = ImageStorageReader()
    client = FakeEmbeddingClient()
    processor = ImageEmbeddingProcessor(storage, client, max_image_bytes=1024 * 1024)

    assert processor.should_process(sample_event) is False
    with pytest.raises(NonRetryableProcessingError, match="Image embedding processing is disabled"):
        await processor.process(sample_event)

    assert client.calls == 0


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

    with pytest.raises(NonRetryableProcessingError, match="maximum embedding source size"):
        await processor.process(sample_event)


@pytest.mark.asyncio
@pytest.mark.parametrize("image_format", ["JPEG", "PNG", "BMP", "GIF"])
async def test_embedding_processor_accepts_pillow_supported_formats(sample_event, image_format):
    sample_event.job_type = "EMBEDDING"
    storage = PillowImageStorageReader(image_format)
    client = FakeEmbeddingClient()
    processor = ImageEmbeddingProcessor(
        storage,
        client,
        input_size=32,
        max_image_bytes=1024 * 1024,
        max_source_pixels=64 * 48,
        direct_decode_max_pixels=64 * 48,
    )

    await processor.process(sample_event)

    assert client.last_pixel_values is not None
    assert client.last_pixel_values.shape == (1, 3, 32, 32)


@pytest.mark.asyncio
async def test_embedding_processor_downsamples_large_jpeg_before_embedding(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = LargeJpegStorageReader()
    client = FakeEmbeddingClient()
    processor = ImageEmbeddingProcessor(
        storage,
        client,
        input_size=32,
        max_image_bytes=1024 * 1024,
        max_source_pixels=512 * 512,
        direct_decode_max_pixels=96 * 96,
    )

    result = await processor.process(sample_event)

    assert result["dimension"] == 768
    assert client.last_pixel_values is not None
    assert client.last_pixel_values.shape == (1, 3, 32, 32)


@pytest.mark.asyncio
async def test_embedding_processor_rejects_large_image_without_safe_decoder_downsample(sample_event):
    sample_event.job_type = "EMBEDDING"
    storage = LargeBmpStorageReader()
    processor = ImageEmbeddingProcessor(
        storage,
        FakeEmbeddingClient(),
        input_size=32,
        max_image_bytes=1024 * 1024,
        max_source_pixels=128 * 128,
        direct_decode_max_pixels=64 * 64,
    )

    with pytest.raises(NonRetryableProcessingError, match="safe embedding decode size"):
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
