import pytest
import uuid
from unittest.mock import AsyncMock, patch, MagicMock
from app.sinks.http import HttpProcessingResultSink
from app.config import settings

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
