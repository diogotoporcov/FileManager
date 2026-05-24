from abc import ABC, abstractmethod
from uuid import UUID

class ProcessingResultSink(ABC):
    @abstractmethod
    async def report_checksum_success(self, job_id: UUID, file_id: UUID, sha256: str):
        pass

    @abstractmethod
    async def report_phash_success(self, job_id: UUID, file_id: UUID, phash: str):
        pass

    @abstractmethod
    async def report_failure(self, job_id: UUID, file_id: UUID, error_message: str):
        pass
