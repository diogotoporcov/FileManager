import json
import logging
from typing import Optional, Any
from aiokafka import AIOKafkaConsumer
from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.worker.flow import ProcessingFlow

logger = logging.getLogger(__name__)

class EventConsumer:
    def __init__(self, flow: ProcessingFlow):
        self.flow = flow
        self.consumer: Optional[Any] = None
        self._should_stop = False

    async def start(self):
        self.consumer = AIOKafkaConsumer(
            settings.kafka_topic_file_processing,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_consumer_group_id,
            value_deserializer=lambda x: json.loads(x.decode('utf-8')),
            enable_auto_commit=False
        )
        await self.consumer.start()
        logger.info(f"Kafka consumer started on {settings.kafka_bootstrap_servers} for topic {settings.kafka_topic_file_processing}")
        
        try:
            async for msg in self.consumer:
                if self._should_stop:
                    break
                    
                logger.info(f"Received event: {msg.value}")
                try:
                    event = FileProcessingRequestedEvent.model_validate(msg.value)
                    await self.flow.run(event)
                    await self.consumer.commit()
                    logger.debug(f"Committed offset for file {event.file_id}")
                except Exception as e:
                    logger.error(f"Failed to process message or commit offset: {e}")
        finally:
            await self.consumer.stop()

    async def stop(self):
        self._should_stop = True
        if self.consumer:
            await self.consumer.stop()
