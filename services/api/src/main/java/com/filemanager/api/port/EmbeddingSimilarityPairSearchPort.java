package com.filemanager.api.port;

import java.util.List;

public interface EmbeddingSimilarityPairSearchPort {
    List<EmbeddingSimilarityPairCandidate> search(EmbeddingSimilarityPairSearchRequest request);
}
