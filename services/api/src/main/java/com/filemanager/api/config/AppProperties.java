package com.filemanager.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
    private final Kafka kafka = new Kafka();
    @Valid
    private final Phash phash = new Phash();
    @Valid
    private final Embedding embedding = new Embedding();
    @Valid
    private final Auth auth = new Auth();
    @Valid
    private final Quota quota = new Quota();

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
            private String fileProcessingRequested = "file.processing.requested";
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
        private boolean enabled = true;

        @NotBlank
        private String modelName = "openai/clip-vit-large-patch14";

        @NotBlank
        private String modelVersion = "1";

        @Min(1)
        private int dimension = 768;

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
