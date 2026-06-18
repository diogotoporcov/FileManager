package com.diogotoporcov.filemanager.api.processing.web.result;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingResultRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validEmbeddingResult_PassesValidation() {
        EmbeddingResultRequest request = EmbeddingResultRequest.builder()
                .fileId(UUID.randomUUID())
                .modelName("openai/clip-vit-large-patch14")
                .modelVersion("1")
                .dimension(3)
                .embedding(List.of(0.1, 0.2, 0.3))
                .build();

        Set<ConstraintViolation<EmbeddingResultRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void wrongEmbeddingLength_FailsValidation() {
        EmbeddingResultRequest request = EmbeddingResultRequest.builder()
                .fileId(UUID.randomUUID())
                .modelName("openai/clip-vit-large-patch14")
                .modelVersion("1")
                .dimension(3)
                .embedding(List.of(0.1, 0.2))
                .build();

        Set<ConstraintViolation<EmbeddingResultRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("embeddingLengthValid");
    }

    @Test
    void missingRequiredFields_FailsValidation() {
        EmbeddingResultRequest request = new EmbeddingResultRequest();

        Set<ConstraintViolation<EmbeddingResultRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
