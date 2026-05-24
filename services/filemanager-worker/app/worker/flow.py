import logging
from typing import List, Dict, Any
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink

from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)

class ProcessingFlow:
    def __init__(self, processors: List[Processor], result_sink: ProcessingResultSink):
        self.processors = {p.name.upper(): p for p in processors}
        self.result_sink = result_sink

    async def run(self, event: FileProcessingRequestedEvent):
        logger.info(f"Starting processing flow for job: {event.processing_job_id} ({event.job_type})")
        
        job_type = event.job_type.upper()
        processor = self.processors.get(job_type)
        
        if not processor:
            raise NonRetryableProcessingError(f"No processor found for job type: {job_type}")

        if not processor.should_process(event):
            raise NonRetryableProcessingError(f"Processor {processor.name} cannot handle file with mime type {event.mime_type}")

        try:
            result = await processor.process(event)
            if not isinstance(result, dict):
                raise NonRetryableProcessingError(
                    f"Processor {processor.name} returned invalid result type: {type(result).__name__}. Expected dict."
                )
        except (RetryableProcessingError, NonRetryableProcessingError):
            raise
        except Exception as e:
            raise RetryableProcessingError(f"Unexpected processing error: {e}")
        
        try:
            if job_type == "CHECKSUM":
                if "sha256" not in result:
                    raise NonRetryableProcessingError(f"Processor {processor.name} did not produce required 'sha256' output")
                
                sha256 = result["sha256"]
                if not isinstance(sha256, str) or len(sha256) != 64 or not all(c in "0123456789abcdef" for c in sha256.lower()):
                    raise NonRetryableProcessingError(f"Processor {processor.name} produced invalid 'sha256' format: {sha256}")

                await self.result_sink.report_checksum_success(
                    event.processing_job_id,
                    event.file_id,
                    sha256
                )
            elif job_type == "PHASH":
                if "phash" not in result:
                    raise NonRetryableProcessingError(f"Processor {processor.name} did not produce required 'phash' output")
                
                phash = result["phash"]
                if not isinstance(phash, str) or len(phash) != 16 or not all(c in "0123456789abcdef" for c in phash.lower()):
                    raise NonRetryableProcessingError(f"Processor {processor.name} produced invalid 'phash' format: {phash}")

                await self.result_sink.report_phash_success(
                    event.processing_job_id,
                    event.file_id,
                    phash
                )

            logger.info(f"Finished processing flow for job: {event.processing_job_id}. Produced: {list(result.keys())}")
            return result
        except NonRetryableProcessingError:
            raise
        except Exception as e:
            # Reporting errors are considered retryable per requirement
            raise RetryableProcessingError(f"Failed to report success: {e}")

    async def report_failure(self, event: FileProcessingRequestedEvent, error_message: str):
        try:
            await self.result_sink.report_failure(
                event.processing_job_id,
                event.file_id,
                error_message
            )
        except Exception as e:
            logger.error(f"Failed to report failure to API for file {event.file_id}: {e}")
            raise RetryableProcessingError(f"Failed to report failure: {e}")
