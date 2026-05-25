package com.filemanager.api.config;

import jakarta.validation.Valid;
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
    private final Auth auth = new Auth();
    @Valid
    private final Quota quota = new Quota();

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
    public static class Auth {
        @NotBlank
        private String providerName = "keycloak";

        private boolean autoLinkExistingUsers = false;

        @NotNull
        private Set<String> trustedAutoLinkProviders = new HashSet<>();
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
