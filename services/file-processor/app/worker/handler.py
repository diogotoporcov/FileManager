import asyncio
import json
import logging
from collections.abc import Mapping
from typing import Final

from pydantic import JsonValue, TypeAdapter

from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.worker.dlq import DeadLetterPublisher, KafkaMessageLike
from app.worker.errors import FailureCategory, NonRetryableProcessingError, ProcessingError, RetryableProcessingError
from app.worker.flow import ProcessingFlow

logger = logging.getLogger(__name__)

JSON_OBJECT_ADAPTER: Final[TypeAdapter[Mapping[str, JsonValue]]] = TypeAdapter(Mapping[str, JsonValue])


class WorkerMessageHandler:
    def __init__(self, flow: ProcessingFlow, dlq_publisher: DeadLetterPublisher):
        self.flow = flow
        self.dlq_publisher = dlq_publisher

    async def handle_message(self, msg: KafkaMessageLike) -> bool:
        """
        Handle a single Kafka message with retries and DLQ fallback.
        Returns True if the message was successfully handled (and should be committed).
        """
        # Parse and validate the incoming message.
        try:
            event = self._parse_event(msg)

        except Exception as exc:
            logger.error(f"Poison message detected: {exc}")
            return await self._handle_poison_message(msg, exc)

        # Execute processing flow with retry logic.
        return await self._process_with_retries(msg, event)

    @staticmethod
    def _parse_event(msg: KafkaMessageLike) -> FileProcessingRequestedEvent:
        raw_value = msg.value

        if isinstance(raw_value, bytes):
            data = JSON_OBJECT_ADAPTER.validate_python(json.loads(raw_value.decode("utf-8")))

        else:
            data = JSON_OBJECT_ADAPTER.validate_python(raw_value)

        return FileProcessingRequestedEvent.model_validate(data)

    async def _handle_poison_message(self, msg: KafkaMessageLike, exc: Exception) -> bool:
        try:
            await self.dlq_publisher.publish_failure(
                original_msg=msg,
                reason=f"Failed to parse event: {exc}",
                category=FailureCategory.POISON,
                attempts=0,
            )
            return True

        except Exception as dlq_error:
            logger.critical(f"CRITICAL: Failed to publish poison message to DLQ: {dlq_error}")
            return False

    async def _process_with_retries(self, msg: KafkaMessageLike, event: FileProcessingRequestedEvent) -> bool:
        attempts = 0
        max_attempts = settings.worker_max_attempts
        backoff = settings.worker_retry_backoff_seconds
        last_error: Exception | None = None

        from app import metrics

        while attempts < max_attempts:
            attempts += 1

            if attempts > 1:
                metrics.RETRIES.inc()

            try:
                await self.flow.run(event)
                metrics.EVENTS_PROCESSED.labels(status="success").inc()
                return True

            except NonRetryableProcessingError as exc:
                logger.warning(f"Non-retryable error on attempt {attempts} for job {event.processing_job_id}: {exc}")
                last_error = exc
                break

            except RetryableProcessingError as exc:
                logger.warning(f"Retryable error on attempt {attempts}/{max_attempts} for job {event.processing_job_id}: {exc}")
                last_error = exc

            except Exception as exc:
                logger.error(f"Unexpected error on attempt {attempts} for job {event.processing_job_id}: {exc}")
                last_error = ProcessingError(str(exc), category=FailureCategory.RETRYABLE)

            if attempts < max_attempts and getattr(last_error, "category", None) == FailureCategory.RETRYABLE:
                await asyncio.sleep(backoff)
                backoff *= settings.worker_retry_backoff_multiplier

            else:
                break

        # Manage terminal failure and report results to API.
        return await self._handle_final_failure(msg, event, last_error, attempts)

    async def _handle_final_failure(
        self,
        msg: KafkaMessageLike,
        event: FileProcessingRequestedEvent,
        last_error: Exception | None,
        attempts: int,
    ) -> bool:
        logger.info(f"Reporting failure to API for job {event.processing_job_id} after {attempts} attempts")
        from app import metrics

        error_class = last_error.__class__.__name__ if last_error else "UnknownError"
        metrics.EVENTS_FAILED.labels(error_class=error_class).inc()
        metrics.EVENTS_PROCESSED.labels(status="failure").inc()

        try:
            await self.flow.report_failure(event, str(last_error))
            return True

        except Exception as report_error:
            logger.error(f"Failed to report failure to API: {report_error}. Sending to DLQ.")
            return await self._publish_to_dlq(msg, event, last_error, report_error, attempts)

    async def _publish_to_dlq(
        self,
        msg: KafkaMessageLike,
        event: FileProcessingRequestedEvent,
        last_error: Exception | None,
        report_error: Exception,
        attempts: int,
    ) -> bool:
        try:
            await self.dlq_publisher.publish_failure(
                original_msg=msg,
                reason=f"Processing failed ({last_error}) and reporting failed ({report_error})",
                category=getattr(last_error, "category", FailureCategory.NON_RETRYABLE),
                attempts=attempts,
                file_id=str(event.file_id),
                processing_job_id=str(event.processing_job_id),
                job_type=event.job_type,
            )
            return True

        except Exception as dlq_error:
            logger.critical(f"CRITICAL: Failed to publish to DLQ: {dlq_error}")
            return False
