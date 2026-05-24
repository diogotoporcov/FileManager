import hashlib
import logging
from typing import Any, Dict
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.storage.base import ObjectStorageReader

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
            raise

        digest = sha256_hash.hexdigest()
        logger.info(f"Computed SHA-256 for file {event.file_id}: {digest}")
        return {"sha256": digest}

class PHashProcessor(Processor):
    @property
    def name(self) -> str:
        return "phash"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        return event.mime_type.startswith("image/")

    async def process(self, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        logger.info(f"Would compute pHash for image {event.file_id} later")
        return {}

class EmbeddingProcessor(Processor):
    @property
    def name(self) -> str:
        return "embedding"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        return event.mime_type.startswith("image/") or event.mime_type == "application/pdf"

    async def process(self, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        logger.info(f"Would generate embedding for file {event.file_id} later")
        return {}
