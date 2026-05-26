package com.filemanager.api.repository;

import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;

public interface DuplicateCandidateMethodCount {
    DetectionMethod getMethod();

    long getTotal();
}
