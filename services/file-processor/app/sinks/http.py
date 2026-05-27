import httpx
import logging
from uuid import UUID
from typing import Any, Dict, Sequence
from app.sinks.base import ProcessingResultSink
from app.config import settings

logger = logging.getLogger(__name__)

class HttpProcessingResultSink(ProcessingResultSink):
    def __init__(self, base_url: str | None = None, internal_api_token: str | None = None):
        self.base_url = str(base_url or settings.metadata_api_base_url).rstrip("/")
        token = internal_api_token or settings.internal_api_token
        self.headers = {
            "Authorization": f"Bearer {token}"
        }
        self._client: httpx.AsyncClient | None = None

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient()

        return self._client

    async def _post(self, url: str, payload: Dict[str, Any]) -> httpx.Response:
        response = await self._get_client().post(url, json=payload, headers=self.headers)
        response.raise_for_status()
        return response

    async def report_checksum_success(self, job_id: UUID, file_id: UUID, sha256: str):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/checksum-result"
        payload = {
            "fileId": str(file_id),
            "sha256": sha256
        }
        try:
            await self._post(url, payload)
            logger.info(f"Reported checksum success for job {job_id}")
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to report checksum success for job {job_id}: status {e.response.status_code}")
            raise
        except httpx.HTTPError as e:
            logger.error(f"Failed to report checksum success for job {job_id}: {type(e).__name__}")
            raise

    async def report_phash_success(self, job_id: UUID, file_id: UUID, phash: str):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/phash-result"
        payload = {
            "fileId": str(file_id),
            "phash": phash
        }
        try:
            await self._post(url, payload)
            logger.info(f"Reported pHash success for job {job_id}")
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to report pHash success for job {job_id}: status {e.response.status_code}")
            raise
        except httpx.HTTPError as e:
            logger.error(f"Failed to report pHash success for job {job_id}: {type(e).__name__}")
            raise

    async def report_embedding_success(
        self,
        job_id: UUID,
        file_id: UUID,
        model_name: str,
        model_version: str,
        dimension: int,
        embedding: Sequence[float],
    ):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/embedding-result"
        payload = {
            "fileId": str(file_id),
            "modelName": model_name,
            "modelVersion": model_version,
            "dimension": dimension,
            "embedding": list(embedding),
        }
        try:
            await self._post(url, payload)
            logger.info(f"Reported embedding success for job {job_id}")
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to report embedding success for job {job_id}: status {e.response.status_code}")
            raise
        except httpx.HTTPError as e:
            logger.error(f"Failed to report embedding success for job {job_id}: {type(e).__name__}")
            raise

    async def report_failure(self, job_id: UUID, file_id: UUID, error_message: str):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/failed"
        payload = {
            "fileId": str(file_id),
            "errorMessage": error_message
        }
        try:
            await self._post(url, payload)
            logger.info(f"Reported failure for job {job_id}")
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to report failure for job {job_id}: status {e.response.status_code}")
            raise
        except httpx.HTTPError as e:
            logger.error(f"Failed to report failure for job {job_id}: {type(e).__name__}")
            raise
