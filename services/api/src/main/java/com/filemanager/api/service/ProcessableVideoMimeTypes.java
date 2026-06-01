package com.filemanager.api.service;

import java.util.Set;

public final class ProcessableVideoMimeTypes {
    private ProcessableVideoMimeTypes() {
    }

    public static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }

        String normalized = mimeType.split(";", 2)[0].trim().toLowerCase();
        
        return processableMimeTypes.stream()
                .map(value -> value.split(";", 2)[0].trim().toLowerCase())
                .anyMatch(normalized::equals);
    }
}
