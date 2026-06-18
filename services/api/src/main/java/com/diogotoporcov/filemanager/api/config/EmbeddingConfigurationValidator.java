package com.diogotoporcov.filemanager.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmbeddingConfigurationValidator implements InitializingBean {

    private final AppProperties appProperties;

    @Override
    public void afterPropertiesSet() {
        int configuredDimension = appProperties.getEmbedding().getDimension();
        if (configuredDimension != EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "Configured embedding dimension " + configuredDimension
                            + " does not match pgvector schema dimension "
                            + EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION
                            + "; changing embedding dimensions requires a schema migration");
        }
    }
}
