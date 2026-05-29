package com.filemanager.api.port;

import com.filemanager.api.entity.DuplicateCandidate;

import java.util.List;

public interface DuplicateCandidateSearchPort {
    List<DuplicateCandidate> search(DuplicateCandidateSearchRequest request);
}
