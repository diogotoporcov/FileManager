import asyncio
import logging
import json
from typing import Any, Optional
from pydantic import ValidationError
from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.worker.flow import ProcessingFlow
from app.worker.dlq import DeadLetterPublisher
from app.worker.errors import FailureCategory, ProcessingError, RetryableProcessingError, NonRetryableProcessingError

logger = logging.getLogger(__name__)

class WorkerMessageHandler:
    def __init__(self, flow: ProcessingFlow, dlq_publisher: DeadLetterPublisher):
        self.flow = flow
        self.dlq_publisher = dlq_publisher

    async def handle_message(self, msg: Any):
        """
        Handle a single Kafka message with retries and DLQ fallback.
        Returns True if the message was successfully handled (and should be committed).
        """
        raw_value = msg.value
        event: Optional[FileProcessingRequestedEvent] = None
        
        # 1. Parse message
        try:
            if isinstance(raw_value, bytes):
                data = json.loads(raw_value.decode("utf-8"))
            else:
                data = raw_value

            event = FileProcessingRequestedEvent.model_validate(data)
        except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, TypeError, ValueError) as e:
            logger.error(f"Poison message detected: {e}")
            try:
                await self.dlq_publisher.publish_failure(
                    original_msg=msg,
                    reason=f"Failed to parse event: {e}",
                    category=FailureCategory.POISON,
                    attempts=0
                )
                return True
            except Exception as dlq_error:
                logger.critical(f"CRITICAL: Failed to publish poison message to DLQ: {dlq_error}")
                return False

        # 2. Retry loop for valid event
        attempts = 0
        max_attempts = settings.worker_max_attempts
        backoff = settings.worker_retry_backoff_seconds
        
        last_error = None
        
        while attempts < max_attempts:
            attempts += 1
            try:
                await self.flow.run(event)
                return True
            except NonRetryableProcessingError as e:
                logger.warning(f"Non-retryable error on attempt {attempts} for job {event.processing_job_id}: {e}")
                last_error = e
                break
            except RetryableProcessingError as e:
                logger.warning(f"Retryable error on attempt {attempts}/{max_attempts} for job {event.processing_job_id}: {e}")
                last_error = e
                if attempts < max_attempts:
                    await asyncio.sleep(backoff)
                    backoff *= settings.worker_retry_backoff_multiplier
                continue
            except Exception as e:
                logger.error(f"Unexpected error on attempt {attempts} for job {event.processing_job_id}: {e}")
                last_error = ProcessingError(str(e), category=FailureCategory.RETRYABLE)
                if attempts < max_attempts:
                    await asyncio.sleep(backoff)
                    backoff *= settings.worker_retry_backoff_multiplier
                continue

        # 3. Handle failure after retries or non-retryable error
        logger.info(f"Reporting failure to API for job {event.processing_job_id} after {attempts} attempts")
        try:
            await self.flow.report_failure(event, str(last_error))
            return True
        except Exception as report_error:
            logger.error(f"Failed to report failure to API: {report_error}. Sending to DLQ.")
            try:
                await self.dlq_publisher.publish_failure(
                    original_msg=msg,
                    reason=f"Processing failed ({last_error}) and reporting failed ({report_error})",
                    category=getattr(last_error, 'category', FailureCategory.NON_RETRYABLE),
                    attempts=attempts,
                    file_id=str(event.file_id),
                    processing_job_id=str(event.processing_job_id),
                    job_type=event.job_type
                )
                return True
            except Exception as dlq_error:
                logger.critical(f"CRITICAL: Failed to publish to DLQ: {dlq_error}")
                return False
