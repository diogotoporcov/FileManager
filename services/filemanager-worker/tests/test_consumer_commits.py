import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from app.worker.consumer import EventConsumer
from app.worker.flow import ProcessingFlow

@pytest.mark.asyncio
async def test_consumer_manual_commit_on_success():
    # Arrange
    flow = MagicMock(spec=ProcessingFlow)
    flow.run = AsyncMock()
    
    consumer = EventConsumer(flow)
    
    # Mock AIOKafkaConsumer
    mock_kafka_consumer = AsyncMock()
    # Mock the iterator behavior of the consumer
    mock_msg = MagicMock()
    mock_msg.value = {
        "eventId": "00000000-0000-0000-0000-000000000001",
        "eventType": "file.processing.requested",
        "occurredAt": "2024-01-01T00:00:00Z",
        "fileId": "00000000-0000-0000-0000-000000000002",
        "processingJobId": "00000000-0000-0000-0000-000000000003",
        "storagePath": "test.jpg",
        "mimeType": "image/jpeg",
        "size": 100,
        "ownerUserId": "00000000-0000-0000-0000-000000000004"
    }

    mock_kafka_consumer.__aiter__.return_value = [mock_msg]
    
    with patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer):
        await consumer.start()
    
    # Assert
    flow.run.assert_called_once()
    mock_kafka_consumer.commit.assert_called_once()

@pytest.mark.asyncio
async def test_consumer_no_commit_on_flow_failure():
    # Arrange
    flow = MagicMock(spec=ProcessingFlow)
    # Simulate a flow failure that propagates (e.g. reporting failed)
    flow.run = AsyncMock(side_effect=Exception("Unrecoverable"))
    
    consumer = EventConsumer(flow)
    
    # Mock AIOKafkaConsumer
    mock_kafka_consumer = AsyncMock()
    mock_msg = MagicMock()
    mock_msg.value = {
        "eventId": "00000000-0000-0000-0000-000000000001",
        "eventType": "file.processing.requested",
        "occurredAt": "2024-01-01T00:00:00Z",
        "fileId": "00000000-0000-0000-0000-000000000002",
        "processingJobId": "00000000-0000-0000-0000-000000000003",
        "storagePath": "test.jpg",
        "mimeType": "image/jpeg",
        "size": 100,
        "ownerUserId": "00000000-0000-0000-0000-000000000004"
    }
    mock_kafka_consumer.__aiter__.return_value = [mock_msg]
    
    with patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer):
        await consumer.start()

    flow.run.assert_called_once()
    mock_kafka_consumer.commit.assert_not_called()
