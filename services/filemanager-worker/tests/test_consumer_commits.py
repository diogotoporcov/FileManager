import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from app.worker.consumer import EventConsumer
from app.worker.flow import ProcessingFlow

from app.worker.handler import WorkerMessageHandler

@pytest.mark.asyncio
async def test_consumer_manual_commit_on_success():
    # Arrange
    handler = MagicMock(spec=WorkerMessageHandler)
    handler.handle_message = AsyncMock(return_value=True)
    
    consumer = EventConsumer(handler)
    
    # Mock AIOKafkaConsumer
    mock_kafka_consumer = AsyncMock()
    # Mock the iterator behavior of the consumer
    mock_msg = MagicMock()
    mock_msg.value = b'{"eventId": "..."}' # Raw bytes since we moved deserialization
    mock_msg.offset = 123

    mock_kafka_consumer.__aiter__.return_value = [mock_msg]
    
    with patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer):
        await consumer.start()
    
    # Assert
    handler.handle_message.assert_called_once_with(mock_msg)
    mock_kafka_consumer.commit.assert_called_once()

@pytest.mark.asyncio
async def test_consumer_no_commit_on_handler_failure():
    # Arrange
    handler = MagicMock(spec=WorkerMessageHandler)
    handler.handle_message = AsyncMock(return_value=False)
    
    consumer = EventConsumer(handler)
    
    # Mock AIOKafkaConsumer
    mock_kafka_consumer = AsyncMock()
    mock_msg = MagicMock()
    mock_msg.value = b'{"eventId": "..."}'
    mock_msg.offset = 124
    mock_kafka_consumer.__aiter__.return_value = [mock_msg]
    
    with patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer):
        await consumer.start()

    handler.handle_message.assert_called_once()
    mock_kafka_consumer.commit.assert_not_called()

@pytest.mark.asyncio
async def test_consumer_no_commit_on_unexpected_handler_exception():
    # Arrange
    handler = MagicMock(spec=WorkerMessageHandler)
    # Simulate an unexpected bug in the handler
    handler.handle_message = AsyncMock(side_effect=RuntimeError("Unexpected bug"))
    
    consumer = EventConsumer(handler)
    
    # Mock AIOKafkaConsumer
    mock_kafka_consumer = AsyncMock()
    mock_msg = MagicMock()
    mock_msg.value = b'{"eventId": "..."}'
    mock_msg.offset = 125
    mock_kafka_consumer.__aiter__.return_value = [mock_msg]
    
    with patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer):
        await consumer.start()

    handler.handle_message.assert_called_once()
    mock_kafka_consumer.commit.assert_not_called()
