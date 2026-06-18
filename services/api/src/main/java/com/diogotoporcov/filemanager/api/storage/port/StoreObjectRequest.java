package com.diogotoporcov.filemanager.api.storage.port;

import lombok.Builder;
import lombok.Value;

import java.io.InputStream;

@Value
@Builder
public class StoreObjectRequest {
    String storagePath;
    InputStream content;
    long size;
    String contentType;
}
