package com.filemanager.api.service;

import java.util.Set;

public final class ProcessableVideoMimeTypes {
    private ProcessableVideoMimeTypes() {
    }

    public static boolean contains(Set<String> processableMimeTypes, String mimeType) {
        return ProcessableAudioMimeTypes.contains(processableMimeTypes, mimeType);
    }
}
