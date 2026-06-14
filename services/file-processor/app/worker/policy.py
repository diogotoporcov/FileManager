from dataclasses import dataclass

from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.processors.audio_mime_types import is_processable_audio_mime_type, parse_processable_audio_mime_types


@dataclass(frozen=True, slots=True)
class ProcessingPolicyDecision:
    enabled: bool
    reason: str | None = None


class WorkerProcessingPolicy:
    def is_enabled(self, event: FileProcessingRequestedEvent) -> ProcessingPolicyDecision:
        job_type = event.job_type.upper()

        if job_type == "CHECKSUM" and not settings.worker_checksum_enabled:
            return ProcessingPolicyDecision(False, "checksum processing is disabled")

        if job_type == "PHASH" and not settings.worker_image_phash_enabled:
            return ProcessingPolicyDecision(False, "image pHash processing is disabled")

        if job_type == "EMBEDDING" and not settings.worker_image_embedding_enabled:
            return ProcessingPolicyDecision(False, "image embedding processing is disabled")

        if job_type == "AUDIO_ANALYSIS":
            return self._audio_analysis_decision(event)

        return ProcessingPolicyDecision(True)

    def _audio_analysis_decision(self, event: FileProcessingRequestedEvent) -> ProcessingPolicyDecision:
        audio_mime_types = parse_processable_audio_mime_types(settings.worker_audio_supported_mime_types)

        if not is_processable_audio_mime_type(event.mime_type, audio_mime_types):
            return ProcessingPolicyDecision(False, "audio analysis only supports audio MIME types")

        if not settings.worker_audio_enabled or not settings.worker_audio_fingerprint_enabled:
            return ProcessingPolicyDecision(False, "audio fingerprint processing is disabled")

        return ProcessingPolicyDecision(True)
