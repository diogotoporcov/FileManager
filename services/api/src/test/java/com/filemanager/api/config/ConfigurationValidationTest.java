package com.filemanager.api.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void minioProperties_Valid() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("admin");
        properties.setSecretKey("password");
        properties.setBucketName("filemanager");

        Set<ConstraintViolation<MinioProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    void minioProperties_BlankValues_Fail() {
        MinioProperties properties = new MinioProperties();
        // All blank

        Set<ConstraintViolation<MinioProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(4);
    }

    @Test
    void minioProperties_InvalidUrl_Fail() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("not-a-url");
        properties.setAccessKey("admin");
        properties.setSecretKey("password");
        properties.setBucketName("filemanager");

        Set<ConstraintViolation<MinioProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("endpoint");
    }

    @Test
    void appProperties_Valid() {
        AppProperties properties = new AppProperties();
        properties.getAuth().setProviderName("keycloak");
        properties.getAuth().getClaims().setEmail("email");
        properties.getAuth().getClaims().setFirstName("given_name");
        properties.getAuth().getClaims().setLastName("family_name");
        properties.getAuth().getClaims().setEmailVerified("email_verified");
        properties.getKafka().getTopics().setFileProcessingRequested("topic");
        properties.getPhash().setThreshold(10);
        properties.getEmbedding().setModelName("openai/clip-vit-large-patch14");
        properties.getEmbedding().setModelVersion("1");
        properties.getEmbedding().setDimension(768);
        properties.getEmbedding().setSimilarityThreshold(0.20);
        properties.getEmbedding().setMaxCandidates(5000);

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    void appProperties_InvalidPhashThreshold_Fail() {
        AppProperties properties = new AppProperties();
        properties.getPhash().setThreshold(65);

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("phash.threshold");

        properties.getPhash().setThreshold(-1);
        violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
    }

    @Test
    void appProperties_BlankKafkaTopic_Fail() {
        AppProperties properties = new AppProperties();
        properties.getKafka().getTopics().setFileProcessingRequested("");

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("kafka.topics.fileProcessingRequested");
    }

    @Test
    void appProperties_InvalidEmbeddingValues_Fail() {
        AppProperties properties = new AppProperties();
        properties.getEmbedding().setModelName("");
        properties.getEmbedding().setModelVersion("");
        properties.getEmbedding().setDimension(0);
        properties.getEmbedding().setSimilarityThreshold(2.1);
        properties.getEmbedding().setMaxCandidates(0);

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(5);
    }

    @Test
    void appProperties_InvalidProcessableImageMimeTypes_Fail() {
        AppProperties properties = new AppProperties();
        properties.setProcessableImageMimeTypes(Set.of());

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("processableImageMimeTypes");

        properties.setProcessableImageMimeTypes(Set.of(""));
        violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("processableImageMimeTypes[].<iterable element>");
    }

    @Test
    void appProperties_BlankAuthValues_Fail() {
        AppProperties properties = new AppProperties();
        properties.getAuth().setProviderName("");
        properties.getAuth().getClaims().setEmail("");
        properties.getAuth().getClaims().setFirstName("");
        properties.getAuth().getClaims().setLastName("");
        properties.getAuth().getClaims().setEmailVerified("");

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(5);
    }

    @Test
    void internalApiProperties_Valid() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setApiToken("12345678901234567890123456789012");

        Set<ConstraintViolation<InternalApiProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    void internalApiProperties_BlankToken_Fail() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setApiToken("");

        Set<ConstraintViolation<InternalApiProperties>> violations = validator.validate(properties);
        assertThat(violations).isNotEmpty();
    }
}
