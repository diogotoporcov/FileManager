package com.filemanager.api.config;

import com.filemanager.api.duplicate.application.DuplicateDetectionProperties;
import com.filemanager.api.duplicate.phash.PhashMih;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PhashMihConfigurationValidator implements InitializingBean {
    private final DuplicateDetectionProperties duplicateDetectionProperties;

    @Override
    public void afterPropertiesSet() {
        PhashMih.validateSupportedThreshold(duplicateDetectionProperties.getImagePhash().getMaxDistance());
    }
}
