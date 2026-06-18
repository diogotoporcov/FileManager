package com.diogotoporcov.filemanager.api.storage.port;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreatePresignedDownloadUrlRequest {
    String storagePath;
    Duration ttl;
    String responseContentDisposition;
    String responseContentType;
}
