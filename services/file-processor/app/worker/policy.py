from dataclasses import dataclass

from app.config import settings
from app.events.models import FileProcessingRequestedEvent
from app.processors.audio_mime_types import is_processable_audio_mime_type, parse_processable_audio_mime_types
from app.processors.video_mime_types import is_processable_video_mime_type, parse_processable_video_mime_types


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

        if job_type == "VIDEO_ANALYSIS":
            return self._video_analysis_decision()

        if job_type == "AUDIO_ANALYSIS":
            return self._audio_analysis_decision(event)

        return ProcessingPolicyDecision(True)

    def _video_analysis_decision(self) -> ProcessingPolicyDecision:
        if not settings.worker_video_enabled or not settings.worker_video_analysis_enabled:
            return ProcessingPolicyDecision(False, "video analysis processing is disabled")

        if not settings.worker_video_frame_phash_enabled and not settings.worker_video_frame_embedding_enabled:
            return ProcessingPolicyDecision(False, "video analysis has no enabled frame outputs")

        return ProcessingPolicyDecision(True)

    def _audio_analysis_decision(self, event: FileProcessingRequestedEvent) -> ProcessingPolicyDecision:
        audio_mime_types = parse_processable_audio_mime_types(settings.worker_audio_supported_mime_types)
        video_mime_types = parse_processable_video_mime_types(settings.worker_video_supported_mime_types)

        if is_processable_video_mime_type(event.mime_type, video_mime_types):
            if not settings.worker_audio_enabled or not settings.worker_video_audio_analysis_enabled:
                return ProcessingPolicyDecision(False, "video audio-track analysis is disabled")

            return ProcessingPolicyDecision(True)

        if is_processable_audio_mime_type(event.mime_type, audio_mime_types):
            if not settings.worker_audio_enabled or not settings.worker_audio_fingerprint_enabled:
                return ProcessingPolicyDecision(False, "standalone audio fingerprint processing is disabled")

            return ProcessingPolicyDecision(True)

        return ProcessingPolicyDecision(True)
