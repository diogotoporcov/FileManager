package com.filemanager.api.processing.web.result;

import com.filemanager.api.processing.domain.result.AudioFingerprint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioAnalysisResultRequest {
    @NotNull
    private UUID fileId;

    @NotNull
    @Positive
    private Long durationMs;

    @NotBlank
    @Size(max = 255)
    private String codec;

    @NotNull
    @Positive
    private Integer sampleRate;

    @NotNull
    @Positive
    private Integer channels;

    @Positive
    private Long bitRate;

    @PositiveOrZero
    private Integer audioStreamIndex;

    @Size(max = 255)
    private String containerFormat;

    @NotBlank
    @Size(max = AudioFingerprint.MAX_FINGERPRINT_LENGTH)
    private String fingerprint;

    @NotBlank
    @Size(max = AudioFingerprint.MAX_FINGERPRINT_ALGORITHM_LENGTH)
    private String fingerprintAlgorithm;

    @NotBlank
    @Size(max = AudioFingerprint.MAX_FINGERPRINT_VERSION_LENGTH)
    private String fingerprintVersion;

    @NotNull
    @Positive
    private Integer fingerprintDurationSeconds;
}
