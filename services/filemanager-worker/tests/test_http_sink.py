import uuid
from unittest.mock import patch, MagicMock

import pytest

from app.sinks.http import HttpProcessingResultSink


@pytest.mark.asyncio
async def test_report_checksum_success_includes_token():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    
    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_post.return_value = mock_response
        
        await sink.report_checksum_success(job_id, file_id, sha256)
        
        assert mock_post.called
        args, kwargs = mock_post.call_args
        assert kwargs["headers"]["Authorization"] == "Bearer test-token"
        assert "X-Internal-Api-Token" not in kwargs["headers"]
        assert kwargs["json"]["sha256"] == sha256

@pytest.mark.asyncio
async def test_report_phash_success_includes_token():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    phash = "fedcba9876543210"
    
    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_post.return_value = mock_response
        
        await sink.report_phash_success(job_id, file_id, phash)
        
        assert mock_post.called
        args, kwargs = mock_post.call_args
        assert kwargs["headers"]["Authorization"] == "Bearer test-token"
        assert "X-Internal-Api-Token" not in kwargs["headers"]
        assert kwargs["json"]["phash"] == phash

@pytest.mark.asyncio
async def test_report_failure_includes_token():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    error_message = "test error"
    
    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_post.return_value = mock_response
        
        await sink.report_failure(job_id, file_id, error_message)
        
        assert mock_post.called
        args, kwargs = mock_post.call_args
        assert kwargs["headers"]["Authorization"] == "Bearer test-token"
        assert "X-Internal-Api-Token" not in kwargs["headers"]
        assert kwargs["json"]["errorMessage"] == error_message

@pytest.mark.asyncio
async def test_report_checksum_failure_does_not_log_token(caplog):
    import httpx
    sink = HttpProcessingResultSink(internal_api_token="secret-token-123")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    
    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.status_code = 500
        mock_post.side_effect = httpx.HTTPStatusError("Internal Error", request=MagicMock(), response=mock_response)
        
        with pytest.raises(httpx.HTTPStatusError):
            await sink.report_checksum_success(job_id, file_id, sha256)
        
        for record in caplog.records:
            assert "secret-token-123" not in record.message
            assert "Authorization" not in record.message
            assert str(job_id) in record.message
            assert "status 500" in record.message

@pytest.mark.asyncio
async def test_report_phash_failure_does_not_log_token(caplog):
    import httpx
    sink = HttpProcessingResultSink(internal_api_token="secret-token-456")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    phash = "0123456789abcdef"
    
    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.status_code = 401
        mock_post.side_effect = httpx.HTTPStatusError("Unauthorized", request=MagicMock(), response=mock_response)
        
        with pytest.raises(httpx.HTTPStatusError):
            await sink.report_phash_success(job_id, file_id, phash)
        
        for record in caplog.records:
            assert "secret-token-456" not in record.message
            assert "Authorization" not in record.message
            assert str(job_id) in record.message
            assert "status 401" in record.message

@pytest.mark.asyncio
async def test_report_failure_failure_does_not_log_token(caplog):
    import httpx
    sink = HttpProcessingResultSink(internal_api_token="secret-token-789")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    
    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.status_code = 403
        mock_post.side_effect = httpx.HTTPStatusError("Forbidden", request=MagicMock(), response=mock_response)
        
        with pytest.raises(httpx.HTTPStatusError):
            await sink.report_failure(job_id, file_id, "Some error")
        
        for record in caplog.records:
            assert "secret-token-789" not in record.message
            assert "Authorization" not in record.message
            assert str(job_id) in record.message
            assert "status 403" in record.message
