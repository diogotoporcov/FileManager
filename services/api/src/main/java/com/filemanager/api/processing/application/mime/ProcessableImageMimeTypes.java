package com.filemanager.api.processing.application.mime;

import java.util.Set;

public final class ProcessableImageMimeTypes {

    private ProcessableImageMimeTypes() {
    }

    public static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        return ProcessableMimeTypes.contains(processableMimeTypes, mimeType);
    }
}
