package com.filemanager.api.duplicate.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "duplicate-detection")
@Validated
@Getter
@Setter
public class DuplicateDetectionProperties {
    @Valid
    private final Exact exact = new Exact();
    @Valid
    private final ImagePhash imagePhash = new ImagePhash();
    @Valid
    private final ImageEmbedding imageEmbedding = new ImageEmbedding();
    @Valid
    private final AudioFingerprint audioFingerprint = new AudioFingerprint();

    @Getter
    @Setter
    public static class Exact {
        private boolean enabled = true;
        @Min(1)
        @Max(1000)
        private int pageSize = 100;
        @Min(1)
        @Max(500)
        private int maxGroups = 50;

        public void setMaxCandidates(int maxCandidates) {
            this.pageSize = maxCandidates;
        }
    }

    @Getter
    @Setter
    public static class ImagePhash {
        private boolean enabled = true;
        @Min(0)
        @Max(64)
        private int maxDistance = 10;
        @Min(1)
        @Max(1000)
        private int pageSize = 100;
        private boolean groupedEnabled = false;

        public void setMaxCandidates(int maxCandidates) {
            this.pageSize = maxCandidates;
        }
    }

    @Getter
    @Setter
    public static class ImageEmbedding {
        private boolean enabled = true;
        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double maxDistance = 0.20;
        @Min(1)
        @Max(1000)
        private int pageSize = 100;
        @Min(1)
        @Max(100_000)
        private int searchWindow = 1000;
        @Min(1)
        @Max(100_000)
        private int maxSearchWindow = 10_000;
        private boolean groupedEnabled = false;

        public void setMaxCandidates(int maxCandidates) {
            this.pageSize = maxCandidates;
        }
    }

    @Getter
    @Setter
    public static class AudioFingerprint {
        private boolean enabled = true;
        @Min(1)
        @Max(1000)
        private int pageSize = 100;

        public void setMaxCandidates(int maxCandidates) {
            this.pageSize = maxCandidates;
        }
    }
}
