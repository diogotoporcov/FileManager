package com.filemanager.api.processing.application.mime;

import java.util.Set;

public final class ProcessableVideoMimeTypes {

    private ProcessableVideoMimeTypes() {
    }

    public static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        return ProcessableMimeTypes.contains(processableMimeTypes, mimeType);
    }
}
