package com.filemanager.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingConfigurationValidatorTest {

    @Test
    void afterPropertiesSet_MatchingSchemaDimension_Passes() {
        AppProperties properties = new AppProperties();
        properties.getEmbedding().setDimension(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        EmbeddingConfigurationValidator validator = new EmbeddingConfigurationValidator(properties);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSet_MismatchedSchemaDimension_FailsClearly() {
        AppProperties properties = new AppProperties();
        properties.getEmbedding().setDimension(512);
        EmbeddingConfigurationValidator validator = new EmbeddingConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match pgvector schema dimension");
    }
}
