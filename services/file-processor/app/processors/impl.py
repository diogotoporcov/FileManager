import hashlib
import logging
import tempfile
import warnings
from typing import Any, Dict
from PIL import Image, ImageFile
import imagehash
from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.processors.image_mime_types import is_processable_image_mime_type, parse_processable_image_mime_types
from app.storage.base import ObjectStorageReader

from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)

Image.MAX_IMAGE_PIXELS = 89_478_485
ImageFile.LOAD_TRUNCATED_IMAGES = False

class ChecksumProcessor(Processor):
    def __init__(self, storage_reader: ObjectStorageReader):
        self.storage_reader = storage_reader

    @property
    def name(self) -> str:
        return "checksum"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        return True

    async def process(self, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        logger.info(f"Computing SHA-256 for file {event.file_id}")
        sha256_hash = hashlib.sha256()
        
        try:
            async for chunk in self.storage_reader.read_object(event.storage_path):
                sha256_hash.update(chunk)
        except Exception as e:
            logger.error(f"Failed to read file from storage: {e}")
            raise RetryableProcessingError(f"Failed to read file from storage: {e}")

        digest = sha256_hash.hexdigest()
        logger.info(f"Computed SHA-256 for file {event.file_id}: {digest}")
        return {"sha256": digest}

class PHashProcessor(Processor):
    def __init__(self, storage_reader: ObjectStorageReader, max_image_bytes: int | None = None):
        self.storage_reader = storage_reader
        self.max_image_bytes = max_image_bytes or settings.worker_phash_max_image_bytes
        self.processable_image_mime_types = parse_processable_image_mime_types(settings.processable_image_mime_types)

    @property
    def name(self) -> str:
        return "phash"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        return is_processable_image_mime_type(event.mime_type, self.processable_image_mime_types)

    async def process(self, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        logger.info(f"Computing pHash for image {event.file_id}")
        
        total_bytes = 0
        try:
            with tempfile.SpooledTemporaryFile(max_size=min(self.max_image_bytes, 1024 * 1024)) as buffer:
                async for chunk in self.storage_reader.read_object(event.storage_path):
                    total_bytes += len(chunk)
                    if total_bytes > self.max_image_bytes:
                        raise NonRetryableProcessingError("Image exceeds maximum pHash processing size")
                    buffer.write(chunk)

                buffer.seek(0)
                try:
                    with warnings.catch_warnings(record=False):
                        warnings.simplefilter("error", Image.DecompressionBombWarning)
                        with Image.open(buffer) as img:
                            img.verify()
                        buffer.seek(0)
                        with Image.open(buffer) as img:
                            phash = imagehash.phash(img)
                            phash_str = str(phash).lower()

                    if len(phash_str) != 16 or not all(c in "0123456789abcdef" for c in phash_str):
                        raise NonRetryableProcessingError(f"Invalid pHash format produced: {phash_str}")

                    logger.info(f"Computed pHash for image {event.file_id}: {phash_str}")
                    return {"phash": phash_str}
                except Image.DecompressionBombWarning as e:
                    raise NonRetryableProcessingError(f"Image decompression bomb warning: {e}")
                except Image.DecompressionBombError as e:
                    raise NonRetryableProcessingError(f"Image decompression bomb error: {e}")
                except NonRetryableProcessingError:
                    raise
                except Exception as e:
                    logger.error(f"Failed to compute pHash for file {event.file_id}: {e}")
                    raise NonRetryableProcessingError(f"Corrupt or unsupported image: {e}")
        except NonRetryableProcessingError:
            raise
        except Exception as e:
            logger.error(f"Failed to read file from storage: {e}")
            raise RetryableProcessingError(f"Failed to read file from storage: {e}")
