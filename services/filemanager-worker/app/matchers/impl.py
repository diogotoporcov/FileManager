import logging
from typing import Any, Dict, List
from app.events.models import FileProcessingRequestedEvent
from app.matchers.base import Matcher

logger = logging.getLogger(__name__)

class ExactHashMatcher(Matcher):
    @property
    def name(self) -> str:
        return "exact_hash"

    async def match(self, event: FileProcessingRequestedEvent, derived_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        if "sha256" not in derived_data:
            return []
        
        logger.info(f"Searching for exact hash matches for {event.file_id}")
        return []

class PHashMatcher(Matcher):
    @property
    def name(self) -> str:
        return "phash"

    async def match(self, event: FileProcessingRequestedEvent, derived_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        if "phash" not in derived_data:
            return []
            
        logger.info(f"Searching for pHash similarity matches for {event.file_id}")
        return []

class EmbeddingMatcher(Matcher):
    @property
    def name(self) -> str:
        return "embedding"

    async def match(self, event: FileProcessingRequestedEvent, derived_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        if "embedding" not in derived_data:
            return []
            
        logger.info(f"Searching for vector similarity matches for {event.file_id}")
        return []
