package com.filemanager.api.processing.application.policy;

import com.filemanager.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GlobalProcessingPolicyResolver implements ProcessingPolicyResolver {

    private final AppProperties appProperties;

    @Override
    public boolean isEnabled(ProcessingCapability capability, ProcessingPolicyContext context) {
        AppProperties.Processing processing = appProperties.getProcessing();

        return switch (capability) {
            case CHECKSUM -> processing.getChecksum().isEnabled();
            case IMAGE_PHASH -> processing.getImage().isPhashEnabled();
            case IMAGE_EMBEDDING -> processing.getImage().isEmbeddingEnabled();
            case AUDIO_FINGERPRINT -> processing.getAudio().isFingerprintEnabled();
        };
    }
}
