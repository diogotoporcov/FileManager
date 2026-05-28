package com.filemanager.api.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessableImageMimeTypesTest {

    @Test
    void contains_ShouldNormalizeCaseWhitespaceAndParameters() {
        Set<String> supportedMimeTypes = Set.of(" image/jpeg ", "IMAGE/X-ICON");

        assertThat(ProcessableImageMimeTypes.contains(supportedMimeTypes, "IMAGE/JPEG; charset=binary")).isTrue();
        assertThat(ProcessableImageMimeTypes.contains(supportedMimeTypes, " image/x-icon ")).isTrue();
    }

    @Test
    void contains_ShouldRejectBlankMissingOrUnsupportedMimeTypes() {
        Set<String> supportedMimeTypes = Set.of("image/jpeg");

        assertThat(ProcessableImageMimeTypes.contains(supportedMimeTypes, null)).isFalse();
        assertThat(ProcessableImageMimeTypes.contains(supportedMimeTypes, "")).isFalse();
        assertThat(ProcessableImageMimeTypes.contains(Set.of(), "image/jpeg")).isFalse();
        assertThat(ProcessableImageMimeTypes.contains(supportedMimeTypes, "image/svg+xml")).isFalse();
    }
}
