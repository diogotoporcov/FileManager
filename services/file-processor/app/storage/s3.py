import asyncio
from collections.abc import AsyncIterator
from typing import Protocol

import boto3
from botocore.config import Config

from app.config import settings
from app.storage.base import StorageObjectReader, StorageObjectReference


class StreamingBody(Protocol):
    def read(self, amt: int | None = None) -> bytes:
        pass

    def close(self) -> None:
        pass


class S3ObjectStorageReader(StorageObjectReader):
    def __init__(self):
        self.s3_client = boto3.client(
            "s3",
            endpoint_url=str(settings.s3_endpoint),
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            config=Config(
                connect_timeout=settings.s3_connect_timeout_seconds,
                read_timeout=settings.s3_read_timeout_seconds,
            ),
        )
        self.bucket_name = settings.s3_bucket_name

    async def read_content(self, reference: StorageObjectReference) -> AsyncIterator[bytes]:
        def get_body() -> StreamingBody:
            response = self.s3_client.get_object(Bucket=self.bucket_name, Key=reference.path)

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
