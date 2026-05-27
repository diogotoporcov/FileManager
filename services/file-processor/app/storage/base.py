from abc import ABC, abstractmethod
from typing import AsyncIterator

class ObjectStorageReader(ABC):
    @abstractmethod
    def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        """Read object content as an async stream of bytes."""
        pass
