import logging
import math
import time
from collections.abc import Mapping, Sequence
from typing import TypedDict

from app import metrics
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink
from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError

logger = logging.getLogger(__name__)


class EmbeddingResult(TypedDict):
    modelName: str
    modelVersion: str
    dimension: int
    embedding: Sequence[float]


class ProcessingFlow:
    def __init__(self, processors: Sequence[Processor], result_sink: ProcessingResultSink):
        self.processors = {p.name.upper(): p for p in processors}
        self.result_sink = result_sink

    async def run(self, event: FileProcessingRequestedEvent) -> Mapping[str, object]:
        logger.info(f"Starting processing flow for job: {event.processing_job_id} ({event.job_type})")
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
    async def _execute_processor(processor: Processor, event: FileProcessingRequestedEvent) -> Mapping[str, object]:
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
                failure_type="KnownError",
            ).inc()
            raise

        except Exception as exc:
            metrics.PROCESSOR_FAILURES.labels(
                processor=processor.name,
                failure_type="UnexpectedError",
            ).inc()
            raise RetryableProcessingError(f"Unexpected processing error: {exc}") from exc

        finally:
            proc_duration = time.perf_counter() - proc_start
            metrics.PROCESSOR_DURATION.labels(processor=processor.name).observe(proc_duration)

    async def _report_result(self, event: FileProcessingRequestedEvent, processor: Processor, result: Mapping[str, object]):
        job_type = event.job_type.upper()

        try:
            if job_type == "CHECKSUM":
                sha256 = self._extract_result(result, "sha256", 64, processor.name)
                await self.result_sink.report_checksum_success(event.processing_job_id, event.file_id, sha256)

            elif job_type == "PHASH":
                phash = self._extract_result(result, "phash", 16, processor.name)
                await self.result_sink.report_phash_success(event.processing_job_id, event.file_id, phash)

            elif job_type == "EMBEDDING":
                embedding_result = self._extract_embedding_result(result, processor.name)
                await self.result_sink.report_embedding_success(
                    event.processing_job_id,
                    event.file_id,
                    embedding_result["modelName"],
                    embedding_result["modelVersion"],
                    embedding_result["dimension"],
                    embedding_result["embedding"],
                )

        except (RetryableProcessingError, NonRetryableProcessingError):
            raise

        except Exception as exc:
            raise RetryableProcessingError(f"Failed to report success: {exc}") from exc

    @staticmethod
    def _extract_result(result: Mapping[str, object], key: str, expected_length: int, processor_name: str) -> str:
        if key not in result:
            raise NonRetryableProcessingError(f"Processor {processor_name} did not produce required '{key}' output")

        val = result[key]

        if not isinstance(val, str) or len(val) != expected_length or not all(c in "0123456789abcdef" for c in val.lower()):
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid '{key}' format: {val}")

        return val

    @staticmethod
    def _extract_embedding_result(result: Mapping[str, object], processor_name: str) -> EmbeddingResult:
        required_keys = {"modelName", "modelVersion", "dimension", "embedding"}
        missing = required_keys - result.keys()

        if missing:
            missing_keys = ", ".join(sorted(missing))
            raise NonRetryableProcessingError(
                f"Processor {processor_name} did not produce required embedding output: {missing_keys}"
            )

        model_name = result["modelName"]
        model_version = result["modelVersion"]
        dimension = result["dimension"]
        embedding = result["embedding"]

        if not isinstance(model_name, str) or not model_name.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding model name")

        if not isinstance(model_version, str) or not model_version.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding model version")

        if not isinstance(dimension, int) or dimension <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding dimension")

        if not isinstance(embedding, list) or len(embedding) != dimension:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding vector")

        if not all(isinstance(value, (int, float)) and math.isfinite(float(value)) for value in embedding):
            raise NonRetryableProcessingError(f"Processor {processor_name} produced non-finite embedding values")

        return {
            "modelName": model_name,
            "modelVersion": model_version,
            "dimension": dimension,
            "embedding": [float(value) for value in embedding],
        }

    async def report_failure(self, event: FileProcessingRequestedEvent, error_message: str) -> None:
        try:
            await self.result_sink.report_failure(
                event.processing_job_id,
                event.file_id,
                error_message,
            )

        except Exception as exc:
            logger.error(f"Failed to report failure to API for file {event.file_id}: {exc}")
            raise RetryableProcessingError(f"Failed to report failure: {exc}") from exc
