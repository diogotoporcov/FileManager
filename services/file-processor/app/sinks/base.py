from abc import ABC, abstractmethod
from collections.abc import Mapping, Sequence
from uuid import UUID

from pydantic import JsonValue


class ProcessingResultSink(ABC):
    @abstractmethod
    async def report_checksum_success(self, job_id: UUID, file_id: UUID, sha256: str) -> None:
        pass

    @abstractmethod
    async def report_phash_success(self, job_id: UUID, file_id: UUID, phash: str) -> None:
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
    ) -> None:
        pass

    async def report_audio_analysis_success(
        self,
        job_id: UUID,
        file_id: UUID,
        result: Mapping[str, JsonValue],
    ) -> None:
        pass

    @abstractmethod
    async def report_failure(self, job_id: UUID, file_id: UUID, error_message: str) -> None:
        pass
