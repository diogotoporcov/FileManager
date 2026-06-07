package com.filemanager.api.storage.port;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GetObjectResponse {
    InputStream content;
    long contentLength;
    String contentType;
    String etag;
}
