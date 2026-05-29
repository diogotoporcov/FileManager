import json
import logging
from abc import ABC, abstractmethod
from collections.abc import Mapping
from datetime import datetime, timezone
from typing import NotRequired, Protocol, TypedDict

from aiokafka import AIOKafkaProducer
from pydantic import JsonValue

from app.config import settings

logger = logging.getLogger(__name__)

KafkaMessageValue = bytes | str | Mapping[str, JsonValue]


class KafkaMessageLike(Protocol):
    value: KafkaMessageValue
    key: bytes | None
    topic: str
    partition: int
    offset: int


class DeadLetterPayload(TypedDict):
    originalTopic: str
    originalPartition: int
    originalOffset: int
    originalKey: str | None
    originalValue: str
    failureReason: str
    failureCategory: str
    attempts: int
    occurredAt: str
    fileId: NotRequired[str]
    processingJobId: NotRequired[str]
    jobType: NotRequired[str]


class DeadLetterPublisher(ABC):
    @abstractmethod
    async def publish_failure(
        self,
        original_msg: KafkaMessageLike,
        reason: str,
        category: str,
        attempts: int,
        file_id: str | None = None,
        processing_job_id: str | None = None,
        job_type: str | None = None,
    ) -> None:
        pass


class KafkaDeadLetterPublisher(DeadLetterPublisher):
    def __init__(self):
        self.producer: AIOKafkaProducer | None = None
        self.topic = settings.kafka_topic_dlq

    async def _ensure_producer(self) -> AIOKafkaProducer:
        producer = self.producer

        if producer is None:
            producer = AIOKafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers,
            )
            self.producer = producer
            await producer.start()

        return producer

    async def publish_failure(
        self,
        original_msg: KafkaMessageLike,
        reason: str,
        category: str,
        attempts: int,
        file_id: str | None = None,
        processing_job_id: str | None = None,
        job_type: str | None = None,
    ) -> None:
        producer = await self._ensure_producer()

        raw_value = original_msg.value

        if isinstance(raw_value, bytes):
            try:
                original_value = raw_value.decode("utf-8")

            except UnicodeDecodeError:
                original_value = f"<Binary Data: {len(raw_value)} bytes>"

        elif isinstance(raw_value, str):
            original_value = raw_value

        else:
            original_value = json.dumps(raw_value)

        payload: DeadLetterPayload = {
            "originalTopic": original_msg.topic,
            "originalPartition": original_msg.partition,
            "originalOffset": original_msg.offset,
            "originalKey": original_msg.key.decode("utf-8", errors="replace") if original_msg.key else None,
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
            await producer.send_and_wait(
                self.topic,
                json.dumps(payload).encode("utf-8"),
            )
            from app import metrics

            metrics.DLQ_MESSAGES.inc()
            logger.info(f"Published message to DLQ topic {self.topic}")

        except Exception as exc:
            logger.error(f"Failed to publish to DLQ: {exc}")
            raise

    async def stop(self) -> None:
        if self.producer:
            await self.producer.stop()
