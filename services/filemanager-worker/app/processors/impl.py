import logging
from typing import Any, Dict
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor

logger = logging.getLogger(__name__)

class ChecksumProcessor(Processor):
    @property
    def name(self) -> str:
        return "checksum"

    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        return True

    async def process(self, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        logger.info(f"Would compute checksum for file {event.file_id} later")
        return {}

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
