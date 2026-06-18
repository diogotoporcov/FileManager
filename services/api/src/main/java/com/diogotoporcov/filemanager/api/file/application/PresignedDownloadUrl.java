package com.diogotoporcov.filemanager.api.file.application;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PresignedDownloadUrl {
    String url;
    Instant expiresAt;
    long expiresInSeconds;
    String method;
}
