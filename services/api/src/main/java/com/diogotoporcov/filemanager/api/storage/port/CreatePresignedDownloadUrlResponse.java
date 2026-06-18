package com.diogotoporcov.filemanager.api.storage.port;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreatePresignedDownloadUrlResponse {
    String url;
    Instant expiresAt;
}
