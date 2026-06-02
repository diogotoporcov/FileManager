import uuid
from unittest.mock import AsyncMock, patch, MagicMock

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
async def test_report_embedding_success_includes_token_and_payload():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    embedding = [0.1, 0.2]

    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_post.return_value = mock_response

        await sink.report_embedding_success(
            job_id,
            file_id,
            "openai/clip-vit-large-patch14",
            "1",
            2,
            embedding,
        )

        assert mock_post.called
        args, kwargs = mock_post.call_args
        assert str(args[0]).endswith(f"/internal/processing/jobs/{job_id}/embedding-result")
        assert kwargs["headers"]["Authorization"] == "Bearer test-token"
        assert "X-Internal-Api-Token" not in kwargs["headers"]
        assert kwargs["json"] == {
            "fileId": str(file_id),
            "modelName": "openai/clip-vit-large-patch14",
            "modelVersion": "1",
            "dimension": 2,
            "embedding": embedding,
        }

@pytest.mark.asyncio
async def test_report_video_analysis_success_includes_token_and_payload():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    result = {
        "durationMs": 1000,
        "sampledFrameCount": 1,
        "samplingStrategy": "even_interval:min=1,max=32,target_seconds=10",
        "modelName": "openai/clip-vit-large-patch14",
        "modelVersion": "1",
        "dimension": 2,
        "frames": [{"timestampMs": 500, "frameIndex": 0, "phash": "fedcba9876543210", "embedding": [0.1, 0.2]}],
    }

    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_post.return_value = mock_response

        await sink.report_video_analysis_success(job_id, file_id, result)

        assert mock_post.called
        args, kwargs = mock_post.call_args
        assert str(args[0]).endswith(f"/internal/processing/jobs/{job_id}/video-analysis-result")
        assert kwargs["headers"]["Authorization"] == "Bearer test-token"
        assert kwargs["json"] == {"fileId": str(file_id), **result}

@pytest.mark.asyncio
async def test_report_audio_analysis_success_includes_token_and_payload():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    result = {
        "durationMs": 1000,
        "codec": "mp3",
        "sampleRate": 44100,
        "channels": 2,
        "bitRate": 128000,
        "audioStreamIndex": 0,
        "containerFormat": "mp3",
        "fingerprint": "12345ABC",
        "fingerprintAlgorithm": "chromaprint",
        "fingerprintVersion": "fpcalc",
        "fingerprintDurationSeconds": 60,
    }

    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_post.return_value = mock_response

        await sink.report_audio_analysis_success(job_id, file_id, result)

        assert mock_post.called
        args, kwargs = mock_post.call_args
        assert str(args[0]).endswith(f"/internal/processing/jobs/{job_id}/audio-analysis-result")
        assert kwargs["headers"]["Authorization"] == "Bearer test-token"
        assert kwargs["json"] == {"fileId": str(file_id), **result}

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
async def test_reuses_http_client_between_reports():
    sink = HttpProcessingResultSink(internal_api_token="test-token")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    phash = "fedcba9876543210"

    mock_response = MagicMock()
    mock_response.raise_for_status = MagicMock()
    mock_client = MagicMock()
    mock_client.post = AsyncMock(return_value=mock_response)
    mock_client.aclose = AsyncMock()

    with patch("app.sinks.http.httpx.AsyncClient", return_value=mock_client) as client_factory:
        await sink.report_checksum_success(job_id, file_id, sha256)
        await sink.report_phash_success(job_id, file_id, phash)
        await sink.close()

    client_factory.assert_called_once()
    assert mock_client.post.await_count == 2
    mock_client.aclose.assert_awaited_once()

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

@pytest.mark.asyncio
async def test_report_embedding_failure_does_not_log_token_or_vector(caplog):
    import httpx
    sink = HttpProcessingResultSink(internal_api_token="secret-token-999")
    job_id = uuid.uuid4()
    file_id = uuid.uuid4()
    embedding = [123.456, 789.012]

    with patch("httpx.AsyncClient.post") as mock_post:
        mock_response = MagicMock()
        mock_response.status_code = 500
        mock_post.side_effect = httpx.HTTPStatusError("Internal Error", request=MagicMock(), response=mock_response)

        with pytest.raises(httpx.HTTPStatusError):
            await sink.report_embedding_success(
                job_id,
                file_id,
                "openai/clip-vit-large-patch14",
                "1",
                2,
                embedding,
            )

        for record in caplog.records:
            assert "secret-token-999" not in record.message
            assert "Authorization" not in record.message
            assert "123.456" not in record.message
            assert "789.012" not in record.message
            assert str(job_id) in record.message
            assert "status 500" in record.message
