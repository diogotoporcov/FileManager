package com.diogotoporcov.filemanager.api.processing.application.mime;

import java.util.Locale;
import java.util.Set;

final class ProcessableMimeTypes {
    private ProcessableMimeTypes() {
    }

    static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        if (processableMimeTypes == null || processableMimeTypes.isEmpty() || mimeType == null || mimeType.isBlank()) {
            return false;
        }

        String normalized = normalize(mimeType);

        return processableMimeTypes.stream()
                .map(ProcessableMimeTypes::normalize)
                .anyMatch(normalized::equals);
    }

    private static String normalize(String mimeType) {
        return mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
