import logging
import math
import time
from collections.abc import Sequence
from typing import TypedDict

from pydantic import JsonValue

from app import metrics
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor, ProcessorResult
from app.sinks.base import ProcessingResultSink
from app.worker.errors import NonRetryableProcessingError, RetryableProcessingError
from app.worker.policy import WorkerProcessingPolicy

logger = logging.getLogger(__name__)


class EmbeddingResult(TypedDict):
    modelName: str
    modelVersion: str
    dimension: int
    embedding: Sequence[float]


class ProcessingFlow:
    def __init__(
        self,
        processors: Sequence[Processor],
        result_sink: ProcessingResultSink,
        processing_policy: WorkerProcessingPolicy | None = None,
    ):
        self.processors = {p.name.upper(): p for p in processors}
        self.result_sink = result_sink
        self.processing_policy = processing_policy or WorkerProcessingPolicy()

    async def run(self, event: FileProcessingRequestedEvent) -> ProcessorResult:
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
        decision = self.processing_policy.is_enabled(event)
        if not decision.enabled:
            raise NonRetryableProcessingError(f"Processing capability disabled: {decision.reason}")

        processor = self.processors.get(job_type)

        if not processor:
            raise NonRetryableProcessingError(f"No processor found for job type: {job_type}")

        if not processor.should_process(event):
            raise NonRetryableProcessingError(f"Processor {processor.name} cannot handle file with mime type {event.mime_type}")

        return processor

    @staticmethod
    async def _execute_processor(processor: Processor, event: FileProcessingRequestedEvent) -> ProcessorResult:
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

    async def _report_result(self, event: FileProcessingRequestedEvent, processor: Processor, result: ProcessorResult):
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

            elif job_type == "VIDEO_ANALYSIS":
                video_result = self._extract_video_analysis_result(result, processor.name)
                await self.result_sink.report_video_analysis_success(
                    event.processing_job_id,
                    event.file_id,
                    video_result,
                )

            elif job_type == "AUDIO_ANALYSIS":
                audio_result = self._extract_audio_analysis_result(result, processor.name)
                await self.result_sink.report_audio_analysis_success(
                    event.processing_job_id,
                    event.file_id,
                    audio_result,
                )

        except (RetryableProcessingError, NonRetryableProcessingError):
            raise

        except Exception as exc:
            raise RetryableProcessingError(f"Failed to report success: {exc}") from exc

    @staticmethod
    def _extract_result(result: ProcessorResult, key: str, expected_length: int, processor_name: str) -> str:
        if key not in result:
            raise NonRetryableProcessingError(f"Processor {processor_name} did not produce required '{key}' output")

        val = result[key]

        if not isinstance(val, str):
            raise NonRetryableProcessingError(
                f"Processor {processor_name} produced invalid '{key}' format: expected string, got {type(val).__name__}"
            )

        if len(val) != expected_length:
            raise NonRetryableProcessingError(
                f"Processor {processor_name} produced invalid '{key}' format: "
                f"expected {expected_length} hex characters, got {len(val)}"
            )

        if not all(c in "0123456789abcdef" for c in val.lower()):
            raise NonRetryableProcessingError(
                f"Processor {processor_name} produced invalid '{key}' format: expected hexadecimal string"
            )

        return val

    @staticmethod
    def _extract_embedding_result(result: ProcessorResult, processor_name: str) -> EmbeddingResult:
        required_keys = {"modelName", "modelVersion", "dimension", "embedding"}
        missing = required_keys - result.keys()

        if missing:
            missing_keys = ", ".join(sorted(missing))
            raise NonRetryableProcessingError(
                f"Processor {processor_name} did not produce required embedding output: {missing_keys}"
            )

        model_name = result.get("modelName")
        model_version = result.get("modelVersion")
        dimension = result.get("dimension")
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

    @staticmethod
    def _extract_video_analysis_result(result: ProcessorResult, processor_name: str) -> dict[str, JsonValue]:
        required_keys = {
            "durationMs",
            "sampledFrameCount",
            "samplingStrategy",
            "frames",
        }
        missing = required_keys - result.keys()
        if missing:
            missing_keys = ", ".join(sorted(missing))
            raise NonRetryableProcessingError(
                f"Processor {processor_name} did not produce required video analysis output: {missing_keys}"
            )

        duration_ms = result["durationMs"]
        sampled_frame_count = result["sampledFrameCount"]
        sampling_strategy = result["samplingStrategy"]
        model_name = result.get("modelName")
        model_version = result.get("modelVersion")
        dimension = result.get("dimension")
        frames = result["frames"]

        if not isinstance(duration_ms, int) or duration_ms <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video duration")

        if not isinstance(sampled_frame_count, int) or sampled_frame_count <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid sampled frame count")

        if not isinstance(sampling_strategy, str) or not sampling_strategy.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid sampling strategy")

        if not isinstance(frames, list) or len(frames) != sampled_frame_count:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video frame list")

        normalized_frames: list[JsonValue] = []
        has_embedding = any(
            isinstance(frame, dict) and isinstance(frame.get("embedding"), list) and len(frame["embedding"]) > 0
            for frame in frames
        )
        if has_embedding:
            if not isinstance(model_name, str) or not model_name.strip():
                raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding model name")

            if not isinstance(model_version, str) or not model_version.strip():
                raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding model version")

            if not isinstance(dimension, int) or dimension <= 0:
                raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid embedding dimension")

        for frame in frames:
            if not isinstance(frame, dict):
                raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video frame item")

            timestamp_ms = frame.get("timestampMs")
            frame_index = frame.get("frameIndex")
            phash = frame.get("phash")
            embedding = frame.get("embedding")

            if not isinstance(timestamp_ms, int) or timestamp_ms < 0:
                raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video frame timestamp")

            if not isinstance(frame_index, int) or frame_index < 0:
                raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video frame index")

            if phash is None and embedding is None:
                raise NonRetryableProcessingError(f"Processor {processor_name} produced video frame without signals")

            normalized_frame: dict[str, JsonValue] = {
                "timestampMs": timestamp_ms,
                "frameIndex": frame_index,
            }
            if phash is not None:
                if not isinstance(phash, str) or len(phash) != 16 or not all(c in "0123456789abcdef" for c in phash.lower()):
                    raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video frame pHash")
                normalized_frame["phash"] = phash.lower()
            if embedding is not None:
                if not has_embedding or not isinstance(embedding, list) or len(embedding) != dimension:
                    raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid video frame embedding")

                if not all(isinstance(value, (int, float)) and math.isfinite(float(value)) for value in embedding):
                    raise NonRetryableProcessingError(f"Processor {processor_name} produced non-finite video embedding values")
                normalized_frame["embedding"] = [float(value) for value in embedding]

            normalized_frames.append(normalized_frame)

        payload: dict[str, JsonValue] = {
            "durationMs": duration_ms,
            "width": ProcessingFlow._optional_positive_int(result.get("width"), processor_name, "video width"),
            "height": ProcessingFlow._optional_positive_int(result.get("height"), processor_name, "video height"),
            "frameCount": ProcessingFlow._optional_non_negative_int(result.get("frameCount"), processor_name, "frame count"),
            "codec": ProcessingFlow._optional_string(result.get("codec")),
            "sampledFrameCount": sampled_frame_count,
            "samplingStrategy": sampling_strategy,
            "frames": normalized_frames,
        }
        if has_embedding:
            payload["modelName"] = model_name
            payload["modelVersion"] = model_version
            payload["dimension"] = dimension

        return payload

    @staticmethod
    def _extract_audio_analysis_result(result: ProcessorResult, processor_name: str) -> dict[str, JsonValue]:
        required_keys = {
            "durationMs",
            "codec",
            "sampleRate",
            "channels",
            "fingerprint",
            "fingerprintAlgorithm",
            "fingerprintVersion",
            "fingerprintDurationSeconds",
        }
        missing = required_keys - result.keys()
        if missing:
            missing_keys = ", ".join(sorted(missing))
            raise NonRetryableProcessingError(
                f"Processor {processor_name} did not produce required audio analysis output: {missing_keys}"
            )

        duration_ms = result["durationMs"]
        codec = result["codec"]
        sample_rate = result["sampleRate"]
        channels = result["channels"]
        fingerprint = result["fingerprint"]
        fingerprint_algorithm = result["fingerprintAlgorithm"]
        fingerprint_version = result["fingerprintVersion"]
        fingerprint_duration_seconds = result["fingerprintDurationSeconds"]

        if not isinstance(duration_ms, int) or duration_ms <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid audio duration")

        if not isinstance(codec, str) or not codec.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid audio codec")

        if not isinstance(sample_rate, int) or sample_rate <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid audio sample rate")

        if not isinstance(channels, int) or channels <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid audio channel count")

        if not isinstance(fingerprint, str) or not fingerprint.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid audio fingerprint")

        if not isinstance(fingerprint_algorithm, str) or not fingerprint_algorithm.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid fingerprint algorithm")

        if not isinstance(fingerprint_version, str) or not fingerprint_version.strip():
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid fingerprint version")

        if not isinstance(fingerprint_duration_seconds, int) or fingerprint_duration_seconds <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid fingerprint duration")

        return {
            "durationMs": duration_ms,
            "codec": codec.strip(),
            "sampleRate": sample_rate,
            "channels": channels,
            "bitRate": ProcessingFlow._optional_positive_int(result.get("bitRate"), processor_name, "audio bit rate"),
            "audioStreamIndex": ProcessingFlow._optional_non_negative_int(
                result.get("audioStreamIndex"),
                processor_name,
                "audio stream index",
            ),
            "containerFormat": ProcessingFlow._optional_string(result.get("containerFormat")),
            "fingerprint": fingerprint.strip(),
            "fingerprintAlgorithm": fingerprint_algorithm.strip(),
            "fingerprintVersion": fingerprint_version.strip(),
            "fingerprintDurationSeconds": fingerprint_duration_seconds,
        }

    @staticmethod
    def _optional_positive_int(value: object, processor_name: str, field_name: str) -> int | None:
        if value is None:
            return None
        if not isinstance(value, int) or value <= 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid {field_name}")

        return value

    @staticmethod
    def _optional_non_negative_int(value: object, processor_name: str, field_name: str) -> int | None:
        if value is None:
            return None
        if not isinstance(value, int) or value < 0:
            raise NonRetryableProcessingError(f"Processor {processor_name} produced invalid {field_name}")

        return value

    @staticmethod
    def _optional_string(value: object) -> str | None:
        if value is None:
            return None
        text = str(value).strip()

        return text or None

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
