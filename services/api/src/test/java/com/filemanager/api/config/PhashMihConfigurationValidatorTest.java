package com.filemanager.api.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemanager.api.duplicate.application.DuplicateDetectionProperties;
import org.junit.jupiter.api.Test;

class PhashMihConfigurationValidatorTest {
    @Test
    void afterPropertiesSet_DefaultThresholdPasses() {
        DuplicateDetectionProperties properties = new DuplicateDetectionProperties();
        PhashMihConfigurationValidator validator = new PhashMihConfigurationValidator(properties);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSet_UnsupportedThresholdFailsClearly() {
        DuplicateDetectionProperties properties = new DuplicateDetectionProperties();
        properties.getImagePhash().setMaxDistance(9);
        PhashMihConfigurationValidator validator = new PhashMihConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not supported");
    }
}
