package com.diogotoporcov.filemanager.api.file.application;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FileDownload {
    String name;
    String mimeType;
    long completeSize;
    long contentLength;
    Long rangeStart;
    Long rangeEnd;
    String etag;
    InputStream content;

    public boolean isPartialContent() {
        return rangeStart != null && rangeEnd != null;
    }
}
