import hashlib
import io
import logging
from typing import Any, Dict
from PIL import Image
import imagehash
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.storage.base import ObjectStorageReader

from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)

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
    def __init__(self, storage_reader: ObjectStorageReader):
        self.storage_reader = storage_reader

    @property
    def name(self) -> str:
        return "phash"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        if not event.mime_type:
            return False
        return event.mime_type.lower().startswith("image/")

    async def process(self, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        logger.info(f"Computing pHash for image {event.file_id}")
        
        buffer = io.BytesIO()
        try:
            async for chunk in self.storage_reader.read_object(event.storage_path):
                buffer.write(chunk)
        except Exception as e:
            logger.error(f"Failed to read file from storage: {e}")
            raise RetryableProcessingError(f"Failed to read file from storage: {e}")
            
        buffer.seek(0)
        try:
            with Image.open(buffer) as img:
                phash = imagehash.phash(img)
                phash_str = str(phash).lower()
                
                if len(phash_str) != 16 or not all(c in "0123456789abcdef" for c in phash_str):
                    raise NonRetryableProcessingError(f"Invalid pHash format produced: {phash_str}")
                    
                logger.info(f"Computed pHash for image {event.file_id}: {phash_str}")
                return {"phash": phash_str}
        except NonRetryableProcessingError:
            raise
        except Exception as e:
            logger.error(f"Failed to compute pHash for file {event.file_id}: {e}")
            raise NonRetryableProcessingError(f"Corrupt or unsupported image: {e}")
