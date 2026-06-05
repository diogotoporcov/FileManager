package com.filemanager.api.processing.web.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class EmbeddingResultRequest {
    @NotNull
    private UUID fileId;

    @NotBlank
    private String modelName;

    @NotBlank
    private String modelVersion;

    @NotNull
    @Positive
    private Integer dimension;

    @NotEmpty
    private List<@NotNull Double> embedding;

    @JsonIgnore
    @AssertTrue(message = "embedding length must equal dimension")
    @SuppressWarnings("unused")
    public boolean isEmbeddingLengthValid() {
        return dimension == null || embedding == null || embedding.size() == dimension;
    }
}
