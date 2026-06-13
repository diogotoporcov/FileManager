package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.duplicate.domain.DuplicateCandidateRefresh;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DuplicateCandidateRefreshRepository extends JpaRepository<DuplicateCandidateRefresh, UUID> {
    boolean existsByOwnerUserIdAndSourceFileIdAndMethodAndModelNameAndModelVersionAndThresholdVersion(
            UUID ownerUserId,
            UUID sourceFileId,
            DuplicateSearchMethod method,
            String modelName,
            String modelVersion,
            String thresholdVersion);

    @Modifying
    @Query(value = """
            INSERT INTO duplicate_candidate_refreshes (
                id,
                owner_user_id,
                source_file_id,
                method,
                model_name,
                model_version,
                threshold_version,
                candidate_count,
                refreshed_at,
                created_at,
                updated_at
            )
            VALUES (
                uuidv7(),
                :ownerUserId,
                :sourceFileId,
                :method,
                :modelName,
                :modelVersion,
                :thresholdVersion,
                :candidateCount,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (
                owner_user_id,
                source_file_id,
                method,
                model_name,
                model_version,
                threshold_version
            )
            DO UPDATE SET
                candidate_count = EXCLUDED.candidate_count,
                refreshed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertRefresh(
            UUID ownerUserId,
            UUID sourceFileId,
            String method,
            String modelName,
            String modelVersion,
            String thresholdVersion,
            int candidateCount);
}
