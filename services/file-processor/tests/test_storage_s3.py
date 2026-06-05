from unittest.mock import MagicMock

import pytest

from app.storage.base import StorageObjectReference
from app.storage.s3 import S3ObjectStorageReader


@pytest.mark.asyncio
async def test_s3_reader_streams_chunks_and_closes_body():
    reader = S3ObjectStorageReader()
    body = MagicMock()
    body.read.side_effect = [b"abc", b"def", b""]
    reader.s3_client = MagicMock()
    reader.s3_client.get_object.return_value = {"Body": body}
    reader.bucket_name = "configured-bucket"

    chunks: list[bytes] = []
    async for chunk in reader.read_content(StorageObjectReference(path="files/a.bin")):
        chunks.append(chunk)

    assert chunks == [b"abc", b"def"]
    reader.s3_client.get_object.assert_called_once_with(Bucket="configured-bucket", Key="files/a.bin")
    body.close.assert_called_once()
