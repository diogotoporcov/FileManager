package com.filemanager.api.processing.application.mime;

import java.util.Set;

public final class ProcessableAudioMimeTypes {

    private ProcessableAudioMimeTypes() {
    }

    public static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        return ProcessableMimeTypes.contains(processableMimeTypes, mimeType);
    }
}
