package com.filemanager.api.port;

import java.util.List;

public interface EmbeddingSimilaritySearchPort {
    List<EmbeddingSimilarityCandidate> search(EmbeddingSimilaritySearchRequest request);
}
