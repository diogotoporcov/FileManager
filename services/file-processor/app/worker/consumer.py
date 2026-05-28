import logging

from aiokafka import AIOKafkaConsumer

from app.config import settings
from app.worker.handler import WorkerMessageHandler

logger = logging.getLogger(__name__)


class EventConsumer:
    def __init__(self, handler: WorkerMessageHandler):
        self.handler = handler
        self.consumer: AIOKafkaConsumer | None = None
        self._should_stop = False

    async def start(self) -> None:
        # Initialize Kafka consumer with manual offset management for reliable delivery.
        self.consumer = AIOKafkaConsumer(
            settings.kafka_topic_file_processing,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_consumer_group_id,
            # Deserialization is handled in WorkerMessageHandler to manage poison messages
            enable_auto_commit=False,
        )
        await self.consumer.start()
        logger.info(f"Kafka consumer started on {settings.kafka_bootstrap_servers} for topic {settings.kafka_topic_file_processing}")

        try:
            async for msg in self.consumer:
                if self._should_stop:
                    break

                logger.info(f"Received message from Kafka (offset: {msg.offset})")

                from app import metrics

                metrics.EVENTS_RECEIVED.inc()

                try:
                    handled = await self.handler.handle_message(msg)

                    # Commit offsets only upon successful processing to ensure At-Least-Once delivery.
                    if handled:
                        await self.consumer.commit()
                        logger.debug(f"Committed offset {msg.offset}")

                    else:
                        logger.error(f"Message at offset {msg.offset} was NOT handled successfully and will NOT be committed.")

                except Exception as exc:
                    logger.exception(f"Unexpected error in message handler for offset {msg.offset}: {exc}")

        finally:
            await self.consumer.stop()

    async def stop(self) -> None:
        self._should_stop = True

        if self.consumer:
            await self.consumer.stop()
