package com.filemanager.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@Validated
public class AppProperties {

    @Valid
    private final Auth auth = new Auth();
    @Valid
    private final Kafka kafka = new Kafka();
    @Valid
    private final Phash phash = new Phash();

    @Getter
    @Setter
    public static class Auth {
        @NotBlank
        private String providerName = "keycloak";
        @Valid
        private final Claims claims = new Claims();

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
    }
}
