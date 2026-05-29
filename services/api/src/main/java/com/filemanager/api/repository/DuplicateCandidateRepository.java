package com.filemanager.api.repository;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DuplicateCandidateRepository extends JpaRepository<DuplicateCandidate, UUID>, JpaSpecificationExecutor<DuplicateCandidate> {
    boolean existsBySourceFileIdAndCandidateFileIdAndDetectionMethod(
            UUID sourceFileId, UUID candidateFileId, DetectionMethod detectionMethod);

    @Query("""
            select dc.detectionMethod as method, count(dc) as total
            from DuplicateCandidate dc
            where (dc.sourceFile.id = :fileId or dc.candidateFile.id = :fileId)
              and dc.sourceFile.deletedAt is null
              and dc.candidateFile.deletedAt is null
            group by dc.detectionMethod
            """)
    List<DuplicateCandidateMethodCount> countActiveByFileIdGroupedByDetectionMethod(UUID fileId);

    @Query("""
            select dc.status as status, count(dc) as total
            from DuplicateCandidate dc
            where (dc.sourceFile.id = :fileId or dc.candidateFile.id = :fileId)
              and dc.sourceFile.deletedAt is null
              and dc.candidateFile.deletedAt is null
            group by dc.status
            """)
    List<DuplicateCandidateStatusCount> countActiveByFileIdGroupedByStatus(UUID fileId);
}
