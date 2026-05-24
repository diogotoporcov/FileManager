from abc import ABC, abstractmethod
from typing import Any, Dict, List
from app.events.models import FileProcessingRequestedEvent

class Matcher(ABC):
    @property
    @abstractmethod
    def name(self) -> str:
        """Name of the matcher."""
        pass

    @abstractmethod
    async def match(self, event: FileProcessingRequestedEvent, derived_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Find potential duplicates or matches based on derived data."""
        pass
