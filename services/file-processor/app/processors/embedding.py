import logging
import tempfile
import warnings
from contextlib import contextmanager
from collections.abc import Iterator, Sequence
from typing import cast

import numpy as np
from PIL import Image, ImageFile, ImageOps

from app.config import settings
from app.embeddings.base import (
    ImageEmbeddingInferenceClient,
    ImageEmbeddingModelOutputError,
    ImageEmbeddingServiceUnavailable,
)
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor, ProcessorResult
from app.processors.image_mime_types import is_processable_image_mime_type, parse_processable_image_mime_types
from app.storage.base import StorageObjectReader
from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)

ImageFile.LOAD_TRUNCATED_IMAGES = False

CLIP_IMAGE_MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
CLIP_IMAGE_STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)


class ImageEmbeddingProcessor(Processor):
    def __init__(
        self,
        storage_reader: StorageObjectReader,
        embedding_client: ImageEmbeddingInferenceClient,
        *,
        model_name: str | None = None,
        model_version: str | None = None,
        input_size: int | None = None,
        embedding_dimension: int | None = None,
        max_image_bytes: int | None = None,
        max_source_pixels: int | None = None,
        direct_decode_max_pixels: int | None = None,
    ):
        self.storage_reader = storage_reader
        self.embedding_client = embedding_client
        self.model_name = model_name or settings.embedding_model_name
        self.model_version = model_version or settings.embedding_model_version
        self.input_size = input_size or settings.embedding_image_input_size
        self.embedding_dimension = embedding_dimension or settings.embedding_dimension
        self.max_image_bytes = max_image_bytes or settings.embedding_max_image_bytes
        self.max_source_pixels = max_source_pixels or settings.embedding_max_source_pixels
        self.direct_decode_max_pixels = direct_decode_max_pixels or settings.embedding_direct_decode_max_pixels
        self.processable_image_mime_types = parse_processable_image_mime_types(settings.processable_image_mime_types)

    @property
    def name(self) -> str:
        return "embedding"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        if event.job_type.upper() != "EMBEDDING":
            return False
        if not settings.worker_image_embedding_enabled:
            return False
        return is_processable_image_mime_type(event.mime_type, self.processable_image_mime_types)

    async def process(self, event: FileProcessingRequestedEvent) -> ProcessorResult:
        if not settings.worker_image_embedding_enabled:
            raise NonRetryableProcessingError("Image embedding processing is disabled")

        logger.info("Computing image embedding for file %s", event.file_id)

        pixel_values = await self._read_and_preprocess(event)

        try:
            model_output = await self.embedding_client.embed_image(pixel_values)

        except ImageEmbeddingServiceUnavailable as exc:
            raise RetryableProcessingError("Triton image embedding inference unavailable") from exc

        except ImageEmbeddingModelOutputError as exc:
            raise NonRetryableProcessingError("Invalid image embedding model output") from exc

        embedding = self._normalize_model_output(model_output)
        logger.info("Computed image embedding for file %s using model %s", event.file_id, self.model_name)
        return {
            "modelName": self.model_name,
            "modelVersion": self.model_version,
            "dimension": self.embedding_dimension,
            "embedding": embedding,
        }

    async def _read_and_preprocess(self, event: FileProcessingRequestedEvent) -> np.ndarray:
        if event.size > self.max_image_bytes:
            raise NonRetryableProcessingError("Image exceeds maximum embedding source size")

        total_bytes = 0

        try:
            with tempfile.SpooledTemporaryFile(max_size=min(self.max_image_bytes, 1024 * 1024)) as buffer:
                async for chunk in self.storage_reader.read_content(event.storage_reference):
                    total_bytes += len(chunk)

                    if total_bytes > self.max_image_bytes:
                        raise NonRetryableProcessingError("Image exceeds maximum embedding source size")

                    buffer.write(chunk)

                buffer.seek(0)

                try:
                    with pillow_max_image_pixels(self.max_source_pixels), warnings.catch_warnings(record=False):
                        warnings.simplefilter("error", Image.DecompressionBombWarning)

                        with Image.open(buffer) as image:
                            image.verify()

                        buffer.seek(0)

                        with Image.open(buffer) as image:
                            prepared = normalize_embedding_image(
                                image,
                                self.input_size,
                                self.max_source_pixels,
                                self.direct_decode_max_pixels,
                            )
                            return preprocess_clip_image(prepared, self.input_size)

                except Image.DecompressionBombWarning as exc:
                    raise NonRetryableProcessingError(f"Image decompression bomb warning: {exc}") from exc

                except Image.DecompressionBombError as exc:
                    raise NonRetryableProcessingError(f"Image decompression bomb error: {exc}") from exc

                except NonRetryableProcessingError:
                    raise

                except Exception as exc:
                    raise NonRetryableProcessingError(f"Corrupt or unsupported image: {exc}") from exc

        except NonRetryableProcessingError:
            raise

        except Exception as exc:
            raise RetryableProcessingError(f"Failed to read file from storage: {type(exc).__name__}") from exc

    def _normalize_model_output(self, model_output: np.ndarray) -> Sequence[float]:
        output = np.asarray(model_output, dtype=np.float32)

        if output.ndim == 2 and output.shape[0] == 1:
            output = output[0]

        if output.ndim != 1:
            raise NonRetryableProcessingError("Image embedding output must be a single vector")

        if output.shape[0] != self.embedding_dimension:
            raise NonRetryableProcessingError("Image embedding output dimension mismatch")

        if not np.all(np.isfinite(output)):
            raise NonRetryableProcessingError("Image embedding output must contain finite values")

        norm = float(np.linalg.norm(output))

        if not np.isfinite(norm) or norm == 0.0:
            raise NonRetryableProcessingError("Image embedding output norm must be finite and non-zero")

        normalized = np.asarray(output / norm, dtype=np.float32)
        return [float(value) for value in normalized.tolist()]


@contextmanager
def pillow_max_image_pixels(max_pixels: int) -> Iterator[None]:
    previous_limit = Image.MAX_IMAGE_PIXELS
    Image.MAX_IMAGE_PIXELS = max_pixels

    try:
        yield

    finally:
        Image.MAX_IMAGE_PIXELS = previous_limit


def normalize_embedding_image(
    image: Image.Image,
    input_size: int,
    max_source_pixels: int,
    direct_decode_max_pixels: int,
) -> Image.Image:
    width, height = cast(tuple[int, int], image.size)
    source_pixels = width * height

    if source_pixels <= 0:
        raise NonRetryableProcessingError("Image dimensions must be positive")

    if source_pixels > max_source_pixels:
        raise NonRetryableProcessingError("Image exceeds maximum embedding source pixel count")

    if source_pixels > direct_decode_max_pixels:
        image.draft("RGB", (input_size * 2, input_size * 2))
        width, height = cast(tuple[int, int], image.size)
        decoded_pixels = width * height

        if decoded_pixels > direct_decode_max_pixels:
            raise NonRetryableProcessingError("Image exceeds safe embedding decode size")

        logger.info(
            "Downsampled large image for embedding decode from %s pixels to %s pixels",
            source_pixels,
            decoded_pixels,
        )

    transposed = ImageOps.exif_transpose(image)
    if transposed is None:
        raise NonRetryableProcessingError("Failed to transpose image using EXIF metadata")

    return transposed.convert("RGB")


def preprocess_clip_image(image: Image.Image, input_size: int) -> np.ndarray:
    image = image.convert("RGB")
    width, height = cast(tuple[int, int], image.size)
    shortest_edge = min(width, height)

    if shortest_edge <= 0:
        raise NonRetryableProcessingError("Image dimensions must be positive")

    scale = input_size / shortest_edge
    resized_width = max(input_size, round(width * scale))
    resized_height = max(input_size, round(height * scale))
    image = image.resize((resized_width, resized_height), resample=Image.Resampling.BICUBIC)

    left = (resized_width - input_size) // 2
    top = (resized_height - input_size) // 2
    image = image.crop((left, top, left + input_size, top + input_size))

    array = np.asarray(image, dtype=np.float32) / 255.0
    array = (array - CLIP_IMAGE_MEAN) / CLIP_IMAGE_STD
    array = np.transpose(array, (2, 0, 1))[np.newaxis, :, :, :]

    return np.ascontiguousarray(array, dtype=np.float32)
