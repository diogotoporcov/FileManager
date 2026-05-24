package com.filemanager.api.repository;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DuplicateCandidateRepository extends JpaRepository<DuplicateCandidate, UUID>, JpaSpecificationExecutor<DuplicateCandidate> {
    boolean existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
            UUID sourceFileId, UUID candidateFileId, DetectionMethod detectionMethod);
}
