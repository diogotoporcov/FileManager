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
import com.filemanager.api.duplicate.application.DuplicateDetectionProperties;
import com.filemanager.api.storage.config.MinioProperties;

import java.util.Map;
import java.util.Set;
import java.time.Duration;

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
    void fileTransferProperties_Valid() {
        FileTransferProperties properties = new FileTransferProperties();
        properties.getPresignedDownload().setEnabled(true);
        properties.getPresignedDownload().setTtl(Duration.ofMinutes(5));

        Set<ConstraintViolation<FileTransferProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void fileTransferProperties_InvalidTtl_Fail() {
        FileTransferProperties properties = new FileTransferProperties();
        properties.getPresignedDownload().setTtl(Duration.ZERO);

        Set<ConstraintViolation<FileTransferProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);

        properties.getPresignedDownload().setTtl(Duration.ofHours(2));
        violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
    }

    @Test
    void fileTransferProperties_PresignedDownloadBindsCorrectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "file-transfer.presigned-download.enabled", "false",
                "file-transfer.presigned-download.ttl", "10m"
        ));

        FileTransferProperties properties = new Binder(source)
                .bind("file-transfer", Bindable.of(FileTransferProperties.class))
                .get();

        assertThat(properties.getPresignedDownload().isEnabled()).isFalse();
        assertThat(properties.getPresignedDownload().getTtl()).isEqualTo(Duration.ofMinutes(10));
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
    void duplicateDetectionProperties_Valid() {
        DuplicateDetectionProperties properties = new DuplicateDetectionProperties();

        Set<ConstraintViolation<DuplicateDetectionProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void duplicateDetectionProperties_InvalidBounds_Fail() {
        DuplicateDetectionProperties properties = new DuplicateDetectionProperties();
        properties.getExact().setMaxCandidates(0);
        properties.getImagePhash().setMaxDistance(65);
        properties.getImageEmbedding().setMaxDistance(2.1);
        properties.getVideoEmbedding().setMaxDistance(2.1);
        properties.getVideoEmbedding().setMaxCandidates(0);
        properties.getVideoEmbedding().setPoolingStrategy("max");

        Set<ConstraintViolation<DuplicateDetectionProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(6);
    }

    @Test
    void duplicateDetectionProperties_BindCorrectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "duplicate-detection.exact.enabled", "false",
                "duplicate-detection.exact.max-candidates", "25",
                "duplicate-detection.image-phash.max-distance", "8",
                "duplicate-detection.image-embedding.max-distance", "0.15",
                "duplicate-detection.audio-fingerprint.grouped-enabled", "false",
                "duplicate-detection.video-embedding.max-distance", "0.12",
                "duplicate-detection.video-embedding.max-candidates", "40",
                "duplicate-detection.video-embedding.pooling-strategy", "mean"
        ));

        DuplicateDetectionProperties properties = new Binder(source)
                .bind("duplicate-detection", Bindable.of(DuplicateDetectionProperties.class))
                .get();

        assertThat(properties.getExact().isEnabled()).isFalse();
        assertThat(properties.getExact().getMaxCandidates()).isEqualTo(25);
        assertThat(properties.getImagePhash().getMaxDistance()).isEqualTo(8);
        assertThat(properties.getImageEmbedding().getMaxDistance()).isEqualTo(0.15);
        assertThat(properties.getAudioFingerprint().isGroupedEnabled()).isFalse();
        assertThat(properties.getVideoEmbedding().getMaxDistance()).isEqualTo(0.12);
        assertThat(properties.getVideoEmbedding().getMaxCandidates()).isEqualTo(40);
        assertThat(properties.getVideoEmbedding().getPoolingStrategy()).isEqualTo("mean");
    }

    @Test
    void duplicateDetectionProperties_VideoEmbeddingConfigValidatesWithoutFrameMatchSetting() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "duplicate-detection.video-embedding.enabled", "true",
                "duplicate-detection.video-embedding.max-distance", "0.20",
                "duplicate-detection.video-embedding.max-candidates", "100",
                "duplicate-detection.video-embedding.grouped-enabled", "false",
                "duplicate-detection.video-embedding.pooling-strategy", "mean"
        ));

        DuplicateDetectionProperties properties = new Binder(source)
                .bind("duplicate-detection", Bindable.of(DuplicateDetectionProperties.class))
                .get();

        Set<ConstraintViolation<DuplicateDetectionProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
        assertThat(properties.getVideoEmbedding().isEnabled()).isTrue();
        assertThat(properties.getVideoEmbedding().getMaxDistance()).isEqualTo(0.20);
        assertThat(properties.getVideoEmbedding().getMaxCandidates()).isEqualTo(100);
        assertThat(properties.getVideoEmbedding().isGroupedEnabled()).isFalse();
        assertThat(properties.getVideoEmbedding().getPoolingStrategy()).isEqualTo("mean");
        assertThat(DuplicateDetectionProperties.VideoEmbedding.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("matched")
                        && field.getName().toLowerCase().contains("frame"));
        assertThat(DuplicateDetectionProperties.VideoEmbedding.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("matched")
                        && method.getName().toLowerCase().contains("frame"));
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
