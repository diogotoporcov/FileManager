package com.filemanager.api.service;

import java.util.Locale;
import java.util.Set;

public final class ProcessableImageMimeTypes {

    private ProcessableImageMimeTypes() {
    }

    public static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        if (processableMimeTypes == null || processableMimeTypes.isEmpty() || mimeType == null || mimeType.isBlank()) {
            return false;
        }

        String normalized = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return processableMimeTypes.stream()
                .map(candidate -> candidate.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }
}
