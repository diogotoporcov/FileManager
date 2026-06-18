package com.diogotoporcov.filemanager.api.file.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Short-lived permission-checked object storage download URL")
public class PresignedDownloadUrlResponse {
    @Schema(description = "Short-lived presigned GET URL")
    String url;
    @Schema(description = "HTTP method to use with the URL")
    String method;
    @Schema(description = "Expiration instant")
    Instant expiresAt;
    @Schema(description = "URL lifetime in seconds")
    long expiresInSeconds;
}
