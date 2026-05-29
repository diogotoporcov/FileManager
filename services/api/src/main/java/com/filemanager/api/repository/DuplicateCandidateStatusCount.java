package com.filemanager.api.repository;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;

public interface DuplicateCandidateStatusCount {
    CandidateStatus getStatus();

    long getTotal();
}
