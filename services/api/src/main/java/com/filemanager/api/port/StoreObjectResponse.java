package com.filemanager.api.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StoreObjectResponse {
    String storagePath;
    String etag;
    String versionId;
}
