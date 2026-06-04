package com.filemanager.api.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;
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
        properties.getKafka().getTopics().setFileProcessingChecksum("checksum-topic");
        properties.getKafka().getTopics().setFileProcessingImage("image-topic");
        properties.getKafka().getTopics().setFileProcessingAudio("audio-topic");
        properties.getKafka().getTopics().setFileProcessingVideo("video-topic");
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
        properties.getKafka().getTopics().setFileProcessingChecksum("");

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("kafka.topics.fileProcessingChecksum");
    }

    @Test
    void appProperties_WorkloadKafkaTopicsBindCorrectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.kafka.topics.file-processing-checksum", "checksum-topic",
                "app.kafka.topics.file-processing-image", "image-topic",
                "app.kafka.topics.file-processing-audio", "audio-topic",
                "app.kafka.topics.file-processing-video", "video-topic"
        ));

        AppProperties properties = new Binder(source)
                .bind("app", Bindable.of(AppProperties.class))
                .get();

        assertThat(properties.getKafka().getTopics().getFileProcessingChecksum()).isEqualTo("checksum-topic");
        assertThat(properties.getKafka().getTopics().getFileProcessingImage()).isEqualTo("image-topic");
        assertThat(properties.getKafka().getTopics().getFileProcessingAudio()).isEqualTo("audio-topic");
        assertThat(properties.getKafka().getTopics().getFileProcessingVideo()).isEqualTo("video-topic");
    }

    @Test
    void appProperties_ProcessingCapabilityTogglesBindCorrectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.processing.checksum.enabled", "false",
                "app.processing.image.phash-enabled", "false",
                "app.processing.image.embedding-enabled", "false",
                "app.processing.video.analysis-enabled", "false",
                "app.processing.video.frame-phash-enabled", "false",
                "app.processing.video.frame-embedding-enabled", "false",
                "app.processing.video.audio-analysis-enabled", "false",
                "app.processing.audio.fingerprint-enabled", "false"
        ));

        AppProperties properties = new Binder(source)
                .bind("app", Bindable.of(AppProperties.class))
                .get();

        assertThat(properties.getProcessing().getChecksum().isEnabled()).isFalse();
        assertThat(properties.getProcessing().getImage().isPhashEnabled()).isFalse();
        assertThat(properties.getProcessing().getImage().isEmbeddingEnabled()).isFalse();
        assertThat(properties.getProcessing().getVideo().isAnalysisEnabled()).isFalse();
        assertThat(properties.getProcessing().getVideo().isFramePhashEnabled()).isFalse();
        assertThat(properties.getProcessing().getVideo().isFrameEmbeddingEnabled()).isFalse();
        assertThat(properties.getProcessing().getVideo().isAudioAnalysisEnabled()).isFalse();
        assertThat(properties.getProcessing().getAudio().isFingerprintEnabled()).isFalse();
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
    void appProperties_InvalidProcessableVideoMimeTypes_Fail() {
        AppProperties properties = new AppProperties();
        properties.setProcessableVideoMimeTypes(Set.of());

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("processableVideoMimeTypes");

        properties.setProcessableVideoMimeTypes(Set.of(""));
        violations = validator.validate(properties);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("processableVideoMimeTypes[].<iterable element>");
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
