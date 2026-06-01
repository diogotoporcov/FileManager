from abc import ABC, abstractmethod
from collections.abc import AsyncIterator
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class StorageObjectReference:
    path: str


class StorageObjectReader(ABC):
    @abstractmethod
    def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        """Read object content as an async stream of bytes."""
        pass
