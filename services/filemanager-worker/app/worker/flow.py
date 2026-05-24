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
        from app import metrics
        import time
        start_time = time.perf_counter()
        
        try:
            processor = self._get_processor(event)
            result = await self._execute_processor(processor, event)
            await self._report_result(event, processor, result)
            
            logger.info(f"Finished processing flow for job: {event.processing_job_id}. Produced: {list(result.keys())}")
            return result
        finally:
            duration = time.perf_counter() - start_time
            metrics.PROCESSING_DURATION.observe(duration)

    def _get_processor(self, event: FileProcessingRequestedEvent) -> Processor:
        job_type = event.job_type.upper()
        processor = self.processors.get(job_type)
        
        if not processor:
            raise NonRetryableProcessingError(f"No processor found for job type: {job_type}")

        if not processor.should_process(event):
            raise NonRetryableProcessingError(f"Processor {processor.name} cannot handle file with mime type {event.mime_type}")
        
        return processor

    @staticmethod
    async def _execute_processor(processor: Processor, event: FileProcessingRequestedEvent) -> Dict[str, Any]:
        from app import metrics
        import time
        proc_start = time.perf_counter()
        try:
            metrics.PROCESSOR_RUNS.labels(processor=processor.name).inc()
            result = await processor.process(event)
            
            if not isinstance(result, dict):
                raise NonRetryableProcessingError(
                    f"Processor {processor.name} returned invalid result type: {type(result).__name__}. Expected dict."
                )
            return result
        except (RetryableProcessingError, NonRetryableProcessingError):
            metrics.PROCESSOR_FAILURES.labels(
                processor=processor.name,
                failure_type="KnownError"
            ).inc()
            raise
        except Exception as e:
            metrics.PROCESSOR_FAILURES.labels(
                processor=processor.name,
                failure_type="UnexpectedError"
            ).inc()
            raise RetryableProcessingError(f"Unexpected processing error: {e}")
        finally:
            proc_duration = time.perf_counter() - proc_start
            metrics.PROCESSOR_DURATION.labels(processor=processor.name).observe(proc_duration)

    async def _report_result(self, event: FileProcessingRequestedEvent, processor: Processor, result: Dict[str, Any]):
        job_type = event.job_type.upper()
        try:
            if job_type == "CHECKSUM":
                sha256 = self._extract_result(result, "sha256", 64, processor.name)
                await self.result_sink.report_checksum_success(event.processing_job_id, event.file_id, sha256)
            elif job_type == "PHASH":
                phash = self._extract_result(result, "phash", 16, processor.name)
                await self.result_sink.report_phash_success(event.processing_job_id, event.file_id, phash)
        except (RetryableProcessingError, NonRetryableProcessingError):
            raise
        except Exception as e:
            raise RetryableProcessingError(f"Failed to report success: {e}")

    @staticmethod
    def _extract_result(result: Dict[str, Any], key: str, expected_length: int, processor_name: str) -> str:
        if key not in result:
            raise NonRetryableProcessingError(f"Processor {processor_name} did not produce required '{key}' output")
        
        val = result[key]
        if not isinstance(val, str) or len(val) != expected_length or not all(c in "0123456789abcdef" for c in val.lower()):
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid '{key}' format: {val}")
        return val

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
