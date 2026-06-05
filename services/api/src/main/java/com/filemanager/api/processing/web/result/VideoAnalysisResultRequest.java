package com.filemanager.api.processing.web.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoAnalysisResultRequest {
    public static final int MAX_FRAMES = 64;

    @NotNull
    private UUID fileId;

    @NotNull
    @Positive
    private Long durationMs;

    @Positive
    private Integer width;

    @Positive
    private Integer height;

    @PositiveOrZero
    private Long frameCount;

    private String codec;

    @NotNull
    @Positive
    private Integer sampledFrameCount;

    @NotBlank
    private String samplingStrategy;

    private String modelName;

    private String modelVersion;

    @Positive
    private Integer dimension;

    @Valid
    @NotEmpty
    @Size(max = MAX_FRAMES)
    private List<@NotNull FrameResult> frames;

    @JsonIgnore
    @AssertTrue(message = "frame count must match sampledFrameCount")
    @SuppressWarnings("unused")
    public boolean isSampledFrameCountValid() {
        return sampledFrameCount == null || frames == null || frames.size() == sampledFrameCount;
    }

    @JsonIgnore
    @AssertTrue(message = "embedding length must equal dimension for every frame")
    @SuppressWarnings("unused")
    public boolean isEmbeddingLengthValid() {
        return dimension == null || frames == null || frames.stream()
                .allMatch(frame -> frame.getEmbedding() == null || frame.getEmbedding().size() == dimension);
    }

    @JsonIgnore
    @AssertTrue(message = "embedding metadata is required when frame embeddings are present")
    @SuppressWarnings("unused")
    public boolean isEmbeddingMetadataValid() {
        if (frames == null || frames.stream()
                .noneMatch(frame -> frame != null && frame.getEmbedding() != null && !frame.getEmbedding().isEmpty())) {
            return true;
        }

        return modelName != null && !modelName.isBlank()
                && modelVersion != null && !modelVersion.isBlank()
                && dimension != null;
    }

    @JsonIgnore
    @AssertTrue(message = "each frame must include pHash or embedding")
    @SuppressWarnings("unused")
    public boolean isFrameSignalValid() {
        return frames == null || frames.stream().allMatch(frame ->
                frame != null
                        && (frame.getPhash() != null && !frame.getPhash().isBlank()
                        || (frame.getEmbedding() != null && !frame.getEmbedding().isEmpty())));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrameResult {
        @NotNull
        @PositiveOrZero
        private Long timestampMs;

        @NotNull
        @PositiveOrZero
        private Integer frameIndex;

        @Pattern(regexp = "^[0-9a-fA-F]{16}$", message = "phash must be a 16-character hex string")
        private String phash;

        private List<@NotNull Double> embedding;
    }
}
