from abc import ABC, abstractmethod
from collections.abc import AsyncIterator


class ObjectStorageReader(ABC):
    @abstractmethod
    def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        """Read object content as an async stream of bytes."""
        pass
