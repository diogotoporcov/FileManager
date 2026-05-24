package com.filemanager.api.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StoredObject {
    String storagePath;
    String bucket;
}
