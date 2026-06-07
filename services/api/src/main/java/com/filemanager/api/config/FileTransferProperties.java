package com.filemanager.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "file-transfer")
@Getter
@Setter
@Validated
public class FileTransferProperties {
    @Valid
    private final PresignedDownload presignedDownload = new PresignedDownload();

    @Getter
    @Setter
    public static class PresignedDownload {
        private boolean enabled = true;

        @NotNull
        private Duration ttl = Duration.ofMinutes(5);

        @AssertTrue(message = "ttl must be positive and no greater than 1 hour")
        public boolean isTtlValid() {
            return ttl != null && !ttl.isNegative() && !ttl.isZero() && ttl.compareTo(Duration.ofHours(1)) <= 0;
        }
    }
}
