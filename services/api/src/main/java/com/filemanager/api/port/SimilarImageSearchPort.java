package com.filemanager.api.port;

import java.util.List;

public interface SimilarImageSearchPort {
    List<SimilarImageCandidate> search(SimilarImageSearchRequest request);
}
