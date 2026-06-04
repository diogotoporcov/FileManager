package com.filemanager.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@Validated
public class AppProperties {

    @Valid
    private final Processing processing = new Processing();
    @Valid
    private final Kafka kafka = new Kafka();
    @Valid
    private final Phash phash = new Phash();
    @Valid
    private final Embedding embedding = new Embedding();
    @Valid
    private final Auth auth = new Auth();
    @Valid
    private final Quota quota = new Quota();
    @NotEmpty
    private Set<@NotBlank String> processableImageMimeTypes = Set.of(
            "image/apng",
            "image/avif",
            "image/bmp",
            "image/gif",
            "image/icns",
            "image/jp2",
            "image/jpeg",
            "image/mpo",
            "image/palm",
            "image/png",
            "image/sgi",
            "image/tiff",
            "image/vnd.adobe.photoshop",
            "image/webp",
            "image/x-icon",
            "image/x-pcx",
            "image/x-portable-anymap",
            "image/x-tga",
            "image/xbm",
            "image/xpm"
    );
    @NotEmpty
    private Set<@NotBlank String> processableVideoMimeTypes = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-msvideo",
            "video/avi",
            "video/matroska",
            "video/x-matroska",
            "video/x-m4v",
            "video/mpeg",
            "video/MP2T",
            "video/3gpp",
            "video/3gpp2"
    );
    @NotEmpty
    private Set<@NotBlank String> processableAudioMimeTypes = Set.of(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/vnd.wave",
            "audio/flac",
            "audio/x-flac",
            "audio/ogg",
            "audio/aac",
            "audio/mp4",
            "audio/x-m4a",
            "audio/webm",
            "audio/opus",
            "audio/matroska",
            "audio/x-matroska",
            "audio/ac3",
            "audio/3gpp",
            "audio/3gpp2",
            "audio/x-aiff",
            "audio/aiff"
    );

    @Getter
    @Setter
    public static class Auth {
        @NotBlank
        private String providerName = "keycloak";

        @Valid
        private final Claims claims = new Claims();

        private boolean autoLinkExistingUsers = false;

        @NotNull
        private Set<String> trustedAutoLinkProviders = new HashSet<>();

        @Getter
        @Setter
        public static class Claims {
            @NotBlank
            private String email = "email";
            @NotBlank
            private String firstName = "given_name";
            @NotBlank
            private String lastName = "family_name";
            @NotBlank
            private String emailVerified = "email_verified";
        }
    }

    @Getter
    @Setter
    public static class Kafka {
        @Valid
        private final Topics topics = new Topics();

        @Getter
        @Setter
        public static class Topics {
            @NotBlank
            private String fileProcessingChecksum = "file.processing.checksum";
            @NotBlank
            private String fileProcessingImage = "file.processing.image";
            @NotBlank
            private String fileProcessingAudio = "file.processing.audio";
            @NotBlank
            private String fileProcessingVideo = "file.processing.video";
        }
    }

    @Getter
    @Setter
    public static class Processing {
        @Valid
        private final Checksum checksum = new Checksum();
        @Valid
        private final Image image = new Image();
        @Valid
        private final Video video = new Video();
        @Valid
        private final Audio audio = new Audio();

        @Getter
        @Setter
        public static class Checksum {
            private boolean enabled = true;
        }

        @Getter
        @Setter
        public static class Image {
            private boolean phashEnabled = true;
            private boolean embeddingEnabled = true;
        }

        @Getter
        @Setter
        public static class Video {
            private boolean analysisEnabled = true;
            private boolean framePhashEnabled = true;
            private boolean frameEmbeddingEnabled = true;
            private boolean audioAnalysisEnabled = true;
        }

        @Getter
        @Setter
        public static class Audio {
            private boolean fingerprintEnabled = true;
        }
    }

    @Getter
    @Setter
    public static class Phash {
        @Min(0)
        @Max(64)
        private int threshold = 10;

        @Min(1)
        private int maxCandidates = 5000;
    }

    @Getter
    @Setter
    public static class Embedding {
        @NotBlank
        private String modelName = "openai/clip-vit-large-patch14";

        @NotBlank
        private String modelVersion = "1";

        @Min(1)
        private int dimension = EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION;

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double similarityThreshold = 0.20;

        @Min(1)
        private int maxCandidates = 5000;
    }

    @Getter
    @Setter
    public static class Quota {
        @Min(1)
        private long userBytes = 15L * 1024 * 1024 * 1024;

        @Min(1)
        private long organizationBytes = 100L * 1024 * 1024 * 1024;
    }
}
