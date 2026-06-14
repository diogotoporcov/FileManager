package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.duplicate.domain.ExactDuplicateGroup;
import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExactDuplicateGroupRepository extends JpaRepository<ExactDuplicateGroup, UUID> {
    Optional<ExactDuplicateGroup> findByOwnerUserIdAndAlgorithmAndHashValue(
            UUID ownerUserId,
            FingerprintAlgorithm algorithm,
            String hashValue);

    @Query("""
            select new com.filemanager.api.duplicate.persistence.ExactDuplicateGroupKeyProjection(
                duplicateGroup.algorithm,
                duplicateGroup.hashValue,
                duplicateGroup.activeFileCount
            )
            from ExactDuplicateGroup duplicateGroup
            where duplicateGroup.ownerUserId = :ownerUserId
                and duplicateGroup.activeFileCount > 1
            order by duplicateGroup.activeFileCount desc,
                duplicateGroup.algorithm asc,
                duplicateGroup.hashValue asc
            """)
    List<ExactDuplicateGroupKeyProjection> findOwnerGroupKeys(UUID ownerUserId, Pageable pageable);

    @Query("""
            select distinct fingerprint.hashValue
            from FileFingerprint fingerprint
            join fingerprint.file file
            where file.ownerUser.id = :ownerUserId
                and fingerprint.algorithm = :algorithm
            """)
    List<String> findHashesForOwner(UUID ownerUserId, FingerprintAlgorithm algorithm);

    void deleteByOwnerUserId(UUID ownerUserId);
}
