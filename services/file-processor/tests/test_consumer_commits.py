from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.worker.consumer import EventConsumer
from app.worker.handler import WorkerMessageHandler


@pytest.mark.asyncio
async def test_consumer_manual_commit_on_success():
    # Arrange
    handler = MagicMock(spec=WorkerMessageHandler)
    handler.handle_message = AsyncMock(return_value=True)

    consumer = EventConsumer(handler)

    mock_kafka_consumer = AsyncMock()
    mock_msg = MagicMock()
    mock_msg.value = b'{"eventId": "..."}'
    mock_msg.offset = 123

    mock_kafka_consumer.__aiter__.return_value = [mock_msg]

    with patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer):
        await consumer.start()

    # Assert
    handler.handle_message.assert_called_once_with(mock_msg)
    mock_kafka_consumer.commit.assert_called_once()


@pytest.mark.asyncio
async def test_consumer_subscribes_to_single_configured_topic():
    handler = MagicMock(spec=WorkerMessageHandler)
    handler.handle_message = AsyncMock(return_value=True)
    consumer = EventConsumer(handler)
    mock_kafka_consumer = AsyncMock()
    mock_kafka_consumer.__aiter__.return_value = []

    with (
        patch("app.worker.consumer.settings.worker_topics", ("file.processing.video",)),
        patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer) as kafka_consumer,
    ):
        await consumer.start()

    kafka_consumer.assert_called_once()
    args, _kwargs = kafka_consumer.call_args
    assert args == ("file.processing.video",)


@pytest.mark.asyncio
async def test_consumer_subscribes_to_multiple_configured_topics():
    handler = MagicMock(spec=WorkerMessageHandler)
    handler.handle_message = AsyncMock(return_value=True)
    consumer = EventConsumer(handler)
    mock_kafka_consumer = AsyncMock()
    mock_kafka_consumer.__aiter__.return_value = []

    with (
        patch("app.worker.consumer.settings.worker_topics", ("file.processing.checksum", "file.processing.image")),
        patch("app.worker.consumer.AIOKafkaConsumer", return_value=mock_kafka_consumer) as kafka_consumer,
    ):
        await consumer.start()

    kafka_consumer.assert_called_once()
    args, _kwargs = kafka_consumer.call_args
    assert args == ("file.processing.checksum", "file.processing.image")


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
