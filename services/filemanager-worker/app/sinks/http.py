import httpx
import logging
from uuid import UUID
from app.sinks.base import ProcessingResultSink
from app.config import settings

logger = logging.getLogger(__name__)

class HttpProcessingResultSink(ProcessingResultSink):
    def __init__(self):
        self.base_url = settings.metadata_api_base_url

    async def report_checksum_success(self, job_id: UUID, file_id: UUID, sha256: str):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/checksum-result"
        payload = {
            "fileId": str(file_id),
            "sha256": sha256
        }
        async with httpx.AsyncClient() as client:
            try:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                logger.info(f"Reported checksum success for job {job_id}")
            except httpx.HTTPError as e:
                logger.error(f"Failed to report checksum success for job {job_id}: {e}")
                raise

    async def report_phash_success(self, job_id: UUID, file_id: UUID, phash: str):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/phash-result"
        payload = {
            "fileId": str(file_id),
            "phash": phash
        }
        async with httpx.AsyncClient() as client:
            try:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                logger.info(f"Reported pHash success for job {job_id}")
            except httpx.HTTPError as e:
                logger.error(f"Failed to report pHash success for job {job_id}: {e}")
                raise

    async def report_failure(self, job_id: UUID, file_id: UUID, error_message: str):
        url = f"{self.base_url}/internal/processing/jobs/{job_id}/failed"
        payload = {
            "fileId": str(file_id),
            "errorMessage": error_message
        }
        async with httpx.AsyncClient() as client:
            try:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                logger.info(f"Reported failure for job {job_id}")
            except httpx.HTTPError as e:
                logger.error(f"Failed to report failure for job {job_id}: {e}")
                raise
