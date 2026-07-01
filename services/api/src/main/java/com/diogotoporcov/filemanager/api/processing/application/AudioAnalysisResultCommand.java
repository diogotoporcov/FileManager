package com.diogotoporcov.filemanager.api.processing.application;

import java.util.UUID;
import lombok.Builder;

@Builder
public record AudioAnalysisResultCommand(
        UUID fileId,
        Long durationMs,
        String codec,
        Integer sampleRate,
        Integer channels,
        Long bitRate,
        Integer audioStreamIndex,
        String containerFormat,
        String fingerprint,
        String fingerprintAlgorithm,
        String fingerprintVersion,
        Integer fingerprintDurationSeconds) {
}
