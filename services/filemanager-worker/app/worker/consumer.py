import logging
from typing import Optional, Any
from aiokafka import AIOKafkaConsumer
from app.config import settings
from app.worker.handler import WorkerMessageHandler

logger = logging.getLogger(__name__)

class EventConsumer:
    def __init__(self, handler: WorkerMessageHandler):
        self.handler = handler
        self.consumer: Optional[Any] = None
        self._should_stop = False

    async def start(self):
        self.consumer = AIOKafkaConsumer(
            settings.kafka_topic_file_processing,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_consumer_group_id,
            # We handle deserialization in WorkerMessageHandler for better poison message handling
            enable_auto_commit=False
        )
        await self.consumer.start()
        logger.info(f"Kafka consumer started on {settings.kafka_bootstrap_servers} for topic {settings.kafka_topic_file_processing}")
        
        try:
            async for msg in self.consumer:
                if self._should_stop:
                    break
                    
                logger.info(f"Received message from Kafka (offset: {msg.offset})")
                
                try:
                    handled = await self.handler.handle_message(msg)
                    
                    if handled:
                        await self.consumer.commit()
                        logger.debug(f"Committed offset {msg.offset}")
                    else:
                        logger.error(f"Message at offset {msg.offset} was NOT handled successfully and will NOT be committed.")
                except Exception as e:
                    logger.exception(f"Unexpected error in message handler for offset {msg.offset}: {e}")
                    # Do not commit, loop continues to next poll
        finally:
            await self.consumer.stop()

    async def stop(self):
        self._should_stop = True
        if self.consumer:
            await self.consumer.stop()
