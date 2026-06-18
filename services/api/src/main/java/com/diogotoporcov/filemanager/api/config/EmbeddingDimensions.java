package com.diogotoporcov.filemanager.api.config;

public final class EmbeddingDimensions {
    // pgvector column dimensions are fixed by migrations; changing this value requires matching schema changes.
    public static final int IMAGE_EMBEDDING_DIMENSION = 768;
    public static final String IMAGE_EMBEDDING_DIMENSION_CHECK = "dimension = " + IMAGE_EMBEDDING_DIMENSION;

    private EmbeddingDimensions() {
    }
}
