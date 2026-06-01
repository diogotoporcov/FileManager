from abc import ABC, abstractmethod
from collections.abc import Mapping
from typing import Any, TypeAlias

from app.events.models import FileProcessingRequestedEvent

ProcessorResultValue: TypeAlias = Any
ProcessorResult: TypeAlias = Mapping[str, ProcessorResultValue]


class Processor(ABC):
    @property
    @abstractmethod
    def name(self) -> str:
        """Name of the processor."""
        pass

    @abstractmethod
    def should_process(self, event: FileProcessingRequestedEvent) -> bool:
        """Check if this processor should handle the file based on its mime type, etc."""
        pass

    @abstractmethod
    async def process(self, event: FileProcessingRequestedEvent) -> ProcessorResult:
        """Process the file and return derived data."""
        pass
