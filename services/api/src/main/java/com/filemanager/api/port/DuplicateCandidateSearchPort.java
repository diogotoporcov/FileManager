package com.filemanager.api.port;

import com.filemanager.api.entity.DuplicateCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DuplicateCandidateSearchPort {
    Page<DuplicateCandidate> search(DuplicateCandidateSearchRequest request, Pageable pageable);
}
