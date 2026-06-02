package com.filemanager.api.dto.internal;

import com.filemanager.api.entity.AudioFingerprint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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
    @Size(max = 64)
    private String fingerprintAlgorithm;

    @NotBlank
    @Size(max = 128)
    private String fingerprintVersion;

    @NotNull
    @Positive
    private Integer fingerprintDurationSeconds;
}
