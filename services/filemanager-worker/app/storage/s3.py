import boto3
import asyncio
from typing import AsyncIterator
from app.storage.base import ObjectStorageReader
from app.config import settings

class S3ObjectStorageReader(ObjectStorageReader):
    def __init__(self):
        self.s3_client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
        )
        self.bucket_name = settings.s3_bucket_name

    async def read_object(self, storage_path: str) -> AsyncIterator[bytes]:
        def get_body():
            response = self.s3_client.get_object(Bucket=self.bucket_name, Key=storage_path)
            return response["Body"]

        body = await asyncio.to_thread(get_body)
        
        try:
            while True:
                chunk = await asyncio.to_thread(body.read, 64 * 1024)
                if not chunk:
                    break
                yield chunk
        finally:
            await asyncio.to_thread(body.close)
