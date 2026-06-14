package com.filemanager.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemanager.api.duplicate.application.DuplicateDetectionProperties;
import com.filemanager.api.storage.config.MinioProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

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

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void minioProperties_BlankValues_Fail() {
        MinioProperties properties = new MinioProperties();

        assertThat(validator.validate(properties)).hasSize(4);
    }

    @Test
    void appProperties_Valid() {
        AppProperties properties = new AppProperties();
        properties.getKafka().getTopics().setFileProcessingChecksum("checksum-topic");
        properties.getKafka().getTopics().setFileProcessingImage("image-topic");
        properties.getKafka().getTopics().setFileProcessingAudio("audio-topic");

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void appProperties_ProcessingCapabilityTogglesBindCorrectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.processing.checksum.enabled", "false",
                "app.processing.image.phash-enabled", "false",
                "app.processing.image.embedding-enabled", "false",
                "app.processing.audio.fingerprint-enabled", "false"
        ));

        AppProperties properties = new Binder(source)
                .bind("app", Bindable.of(AppProperties.class))
                .get();

        assertThat(properties.getProcessing().getChecksum().isEnabled()).isFalse();
        assertThat(properties.getProcessing().getImage().isPhashEnabled()).isFalse();
        assertThat(properties.getProcessing().getImage().isEmbeddingEnabled()).isFalse();
        assertThat(properties.getProcessing().getAudio().isFingerprintEnabled()).isFalse();
    }

    @Test
    void appProperties_InvalidProcessableImageMimeTypes_Fail() {
        AppProperties properties = new AppProperties();
        properties.setProcessableImageMimeTypes(Set.of());

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("processableImageMimeTypes");
    }

    @Test
    void appProperties_InvalidProcessableAudioMimeTypes_Fail() {
        AppProperties properties = new AppProperties();
        properties.setProcessableAudioMimeTypes(Set.of(""));

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("processableAudioMimeTypes[].<iterable element>");
    }

    @Test
    void fileTransferProperties_InvalidTtl_Fail() {
        FileTransferProperties properties = new FileTransferProperties();
        properties.getPresignedDownload().setTtl(Duration.ZERO);

        assertThat(validator.validate(properties)).hasSize(1);
    }

    @Test
    void duplicateDetectionProperties_Valid() {
        assertThat(validator.validate(new DuplicateDetectionProperties())).isEmpty();
    }

    @Test
    void duplicateDetectionProperties_InvalidBounds_Fail() {
        DuplicateDetectionProperties properties = new DuplicateDetectionProperties();
        properties.getExact().setMaxCandidates(0);
        properties.getImagePhash().setMaxDistance(65);
        properties.getImageEmbedding().setMaxDistance(2.1);
        properties.getAudioFingerprint().setMaxCandidates(0);

        assertThat(validator.validate(properties)).hasSize(4);
    }

    @Test
    void duplicateDetectionProperties_BindCorrectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "duplicate-detection.exact.enabled", "false",
                "duplicate-detection.exact.max-candidates", "25",
                "duplicate-detection.image-phash.max-distance", "8",
                "duplicate-detection.image-embedding.max-distance", "0.15",
                "duplicate-detection.audio-fingerprint.max-candidates", "40"
        ));

        DuplicateDetectionProperties properties = new Binder(source)
                .bind("duplicate-detection", Bindable.of(DuplicateDetectionProperties.class))
                .get();

        assertThat(properties.getExact().isEnabled()).isFalse();
        assertThat(properties.getExact().getMaxCandidates()).isEqualTo(25);
        assertThat(properties.getImagePhash().getMaxDistance()).isEqualTo(8);
        assertThat(properties.getImageEmbedding().getMaxDistance()).isEqualTo(0.15);
        assertThat(properties.getAudioFingerprint().getMaxCandidates()).isEqualTo(40);
    }

    @Test
    void internalApiProperties_BlankToken_Fail() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setApiToken("");

        assertThat(validator.validate(properties)).isNotEmpty();
    }
}
