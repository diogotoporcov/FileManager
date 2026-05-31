package com.filemanager.api.port;

import java.util.List;

public interface SimilarImagePairSearchPort {
    List<SimilarImagePairCandidate> search(SimilarImagePairSearchRequest request);
}
