import json
import logging
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Any, Optional
from aiokafka import AIOKafkaProducer
from app.config import settings

logger = logging.getLogger(__name__)

class DeadLetterPublisher(ABC):
    @abstractmethod
    async def publish_failure(
        self,
        original_msg: Any,
        reason: str,
        category: str,
        attempts: int,
        file_id: Optional[str] = None,
        processing_job_id: Optional[str] = None,
        job_type: Optional[str] = None
    ):
        pass

class KafkaDeadLetterPublisher(DeadLetterPublisher):
    def __init__(self):
        self.producer: Optional[AIOKafkaProducer] = None
        self.topic = settings.kafka_topic_dlq

    async def _ensure_producer(self):
        if self.producer is None:
            self.producer = AIOKafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers
            )
            await self.producer.start()

    async def publish_failure(
        self,
        original_msg: Any,
        reason: str,
        category: str,
        attempts: int,
        file_id: Optional[str] = None,
        processing_job_id: Optional[str] = None,
        job_type: Optional[str] = None
    ):
        await self._ensure_producer()
        
        try:
            original_value = original_msg.value.decode('utf-8') if isinstance(original_msg.value, bytes) else str(original_msg.value)
        except UnicodeDecodeError:
            original_value = f"<Binary Data: {len(original_msg.value)} bytes>"

        payload = {
            "originalTopic": original_msg.topic,
            "originalPartition": original_msg.partition,
            "originalOffset": original_msg.offset,
            "originalKey": original_msg.key.decode('utf-8', errors='replace') if original_msg.key else None,
            "originalValue": original_value,
            "failureReason": reason,
            "failureCategory": category,
            "attempts": attempts,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        
        if file_id:
            payload["fileId"] = file_id
        if processing_job_id:
            payload["processingJobId"] = processing_job_id
        if job_type:
            payload["jobType"] = job_type

        try:
            await self.producer.send_and_wait(
                self.topic,
                json.dumps(payload).encode('utf-8')
            )
            logger.info(f"Published message to DLQ topic {self.topic}")
        except Exception as e:
            logger.error(f"Failed to publish to DLQ: {e}")
            raise

    async def stop(self):
        if self.producer:
            await self.producer.stop()
