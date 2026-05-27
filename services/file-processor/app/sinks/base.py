from abc import ABC, abstractmethod
from uuid import UUID
from typing import Sequence

class ProcessingResultSink(ABC):
    @abstractmethod
    async def report_checksum_success(self, job_id: UUID, file_id: UUID, sha256: str):
        pass

    @abstractmethod
    async def report_phash_success(self, job_id: UUID, file_id: UUID, phash: str):
        pass

    @abstractmethod
    async def report_embedding_success(
        self,
        job_id: UUID,
        file_id: UUID,
        model_name: str,
        model_version: str,
        dimension: int,
        embedding: Sequence[float],
    ):
        pass

    @abstractmethod
    async def report_failure(self, job_id: UUID, file_id: UUID, error_message: str):
        pass
