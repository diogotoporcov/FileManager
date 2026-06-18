package com.diogotoporcov.filemanager.api.storage.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GetObjectRequest {
    String storagePath;
    Long rangeStart;
    Long rangeEnd;

    public Long getRangeLength() {
        if (rangeStart == null || rangeEnd == null) {
            return null;
        }

        return rangeEnd - rangeStart + 1;
    }
}
