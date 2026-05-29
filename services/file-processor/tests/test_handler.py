import pytest
import uuid
import json
from collections.abc import Mapping
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch
from typing import Final

from pydantic import JsonValue, TypeAdapter

from app.worker.handler import WorkerMessageHandler
from app.worker.flow import ProcessingFlow
from app.worker.dlq import DeadLetterPublisher
from app.worker.errors import RetryableProcessingError, NonRetryableProcessingError, FailureCategory

JSON_OBJECT_ADAPTER: Final[TypeAdapter[Mapping[str, JsonValue]]] = TypeAdapter(Mapping[str, JsonValue])


@pytest.fixture
def dlq_publisher():
    return MagicMock(spec=DeadLetterPublisher)

@pytest.fixture
def flow():
    return MagicMock(spec=ProcessingFlow)

@pytest.fixture
def handler(flow, dlq_publisher):
    return WorkerMessageHandler(flow, dlq_publisher)

@pytest.fixture
def mock_msg():
    msg = MagicMock()
    msg.topic = "test-topic"
    msg.partition = 0
    msg.offset = 100
    msg.key = b"key"
    msg.value = json.dumps({
        "eventId": str(uuid.uuid4()),
        "eventType": "file.processing.requested",
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "fileId": str(uuid.uuid4()),
        "processingJobId": str(uuid.uuid4()),
        "jobType": "CHECKSUM",
        "storagePath": "test.jpg",
        "mimeType": "image/jpeg",
        "size": 100,
        "ownerUserId": str(uuid.uuid4())
    }).encode('utf-8')
    return msg

@pytest.mark.asyncio
async def test_handle_success(handler, flow, mock_msg):
    flow.run = AsyncMock()
    
    result = await handler.handle_message(mock_msg)
    
    assert result is True
    flow.run.assert_called_once()

@pytest.mark.asyncio
async def test_handle_retry_success(handler, flow, mock_msg):
    flow.run = AsyncMock(side_effect=[RetryableProcessingError("fail"), None])
    
    with patch("asyncio.sleep", AsyncMock()):
        result = await handler.handle_message(mock_msg)
    
    assert result is True
    assert flow.run.call_count == 2

@pytest.mark.asyncio
async def test_handle_retry_exhausted_reports_failure(handler, flow, mock_msg):
    flow.run = AsyncMock(side_effect=RetryableProcessingError("fail"))
    flow.report_failure = AsyncMock()
    
    with patch("asyncio.sleep", AsyncMock()):
        result = await handler.handle_message(mock_msg)
    
    assert result is True
    assert flow.run.call_count == 3 # Default max attempts is 3
    flow.report_failure.assert_called_once()

@pytest.mark.asyncio
async def test_handle_non_retryable_reports_failure(handler, flow, mock_msg):
    flow.run = AsyncMock(side_effect=NonRetryableProcessingError("corrupt"))
    flow.report_failure = AsyncMock()
    
    result = await handler.handle_message(mock_msg)
    
    assert result is True
    flow.run.assert_called_once()
    flow.report_failure.assert_called_once()

@pytest.mark.asyncio
async def test_handle_report_failure_fails_sends_to_dlq(handler, flow, dlq_publisher, mock_msg):
    flow.run = AsyncMock(side_effect=NonRetryableProcessingError("corrupt"))
    flow.report_failure = AsyncMock(side_effect=Exception("API down"))
    dlq_publisher.publish_failure = AsyncMock()
    
    result = await handler.handle_message(mock_msg)
    
    assert result is True
    flow.report_failure.assert_called_once()
    dlq_publisher.publish_failure.assert_called_once()

@pytest.mark.asyncio
async def test_handle_poison_message_sends_to_dlq(handler, dlq_publisher, mock_msg):
    mock_msg.value = b"not-json"
    dlq_publisher.publish_failure = AsyncMock()
    
    result = await handler.handle_message(mock_msg)
    
    assert result is True
    dlq_publisher.publish_failure.assert_called_once()
    args, kwargs = dlq_publisher.publish_failure.call_args
    assert "Failed to parse event" in kwargs["reason"]
    assert kwargs["category"] == FailureCategory.POISON

@pytest.mark.asyncio
async def test_handle_missing_fields_poison_message(handler, dlq_publisher, mock_msg):
    mock_msg.value = b'{"eventId": "00000000-0000-0000-0000-000000000001"}' # Missing fields
    dlq_publisher.publish_failure = AsyncMock()
    
    result = await handler.handle_message(mock_msg)
    
    assert result is True
    dlq_publisher.publish_failure.assert_called_once()
    assert dlq_publisher.publish_failure.call_args[1]["category"] == FailureCategory.POISON

@pytest.mark.asyncio
async def test_handle_invalid_schema_poison_message(handler, dlq_publisher, mock_msg):
    data = dict(JSON_OBJECT_ADAPTER.validate_python(json.loads(mock_msg.value.decode("utf-8"))))
    data["eventId"] = "not-a-uuid"
    mock_msg.value = json.dumps(data).encode("utf-8")
    dlq_publisher.publish_failure = AsyncMock()
    
    result = await handler.handle_message(mock_msg)
    
    assert result is True
    dlq_publisher.publish_failure.assert_called_once()
    assert dlq_publisher.publish_failure.call_args[1]["category"] == FailureCategory.POISON

@pytest.mark.asyncio
async def test_handle_poison_dlq_failure_returns_false(handler, dlq_publisher, mock_msg):
    mock_msg.value = b"not-json"
    dlq_publisher.publish_failure = AsyncMock(side_effect=Exception("DLQ failed"))
    
    result = await handler.handle_message(mock_msg)
    
    assert result is False

@pytest.mark.asyncio
async def test_handle_retry_exhausted_dlq_success_returns_true(handler, flow, dlq_publisher, mock_msg):
    # Retryable failure until exhaustion, then failure report fails, then DLQ succeeds
    flow.run = AsyncMock(side_effect=RetryableProcessingError("fail"))
    flow.report_failure = AsyncMock(side_effect=Exception("API down"))
    dlq_publisher.publish_failure = AsyncMock(return_value=None)
    
    with patch("asyncio.sleep", AsyncMock()):
        result = await handler.handle_message(mock_msg)
    
    assert result is True
    assert flow.run.call_count == 3
    flow.report_failure.assert_called_once()
    dlq_publisher.publish_failure.assert_called_once()

@pytest.mark.asyncio
async def test_handle_retry_exhausted_dlq_failure_returns_false(handler, flow, dlq_publisher, mock_msg):
    # Same as above but DLQ fails
    flow.run = AsyncMock(side_effect=RetryableProcessingError("fail"))
    flow.report_failure = AsyncMock(side_effect=Exception("API down"))
    dlq_publisher.publish_failure = AsyncMock(side_effect=Exception("DLQ failed"))
    
    with patch("asyncio.sleep", AsyncMock()):
        result = await handler.handle_message(mock_msg)
    
    assert result is False
    assert flow.run.call_count == 3
    flow.report_failure.assert_called_once()
    dlq_publisher.publish_failure.assert_called_once()
