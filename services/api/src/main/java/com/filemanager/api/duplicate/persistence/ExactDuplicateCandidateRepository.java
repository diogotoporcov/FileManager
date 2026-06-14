package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.FileFingerprint;
import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExactDuplicateCandidateRepository extends JpaRepository<FileFingerprint, UUID> {
    @Query("""
            select new com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateProjection(
                file.id,
                file.name,
                file.mimeType,
                file.size,
                candidate.algorithm,
                candidate.hashValue,
                file.createdAt
            )
            from FileFingerprint candidate
            join candidate.file file
            left join file.folder folder
            where candidate.algorithm = :algorithm
                and candidate.hashValue = :hashValue
                and file.ownerUser.id = :actorUserId
                and file.id <> :sourceFileId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
                and (
                    :cursorCreatedAt is null
                    or file.createdAt < :cursorCreatedAt
                    or (file.createdAt = :cursorCreatedAt and file.id > :cursorFileId)
                )
            order by file.createdAt desc, file.id
            """)
    List<ExactDuplicateCandidateProjection> findCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            FingerprintAlgorithm algorithm,
            String hashValue,
            OffsetDateTime cursorCreatedAt,
            UUID cursorFileId,
            Pageable pageable);

    @Query("""
            select new com.filemanager.api.duplicate.persistence.ExactDuplicateGroupKeyProjection(
                fingerprint.algorithm,
                fingerprint.hashValue,
                count(file.id)
            )
            from FileFingerprint fingerprint
            join fingerprint.file file
            left join file.folder folder
            where file.ownerUser.id = :actorUserId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
                and (:folderId is null or folder.id = :folderId)
                and (:mimeType is null or file.mimeType = :mimeType)
            group by fingerprint.algorithm, fingerprint.hashValue
            having count(file.id) > 1
            order by count(file.id) desc, fingerprint.algorithm asc, fingerprint.hashValue asc
            """)
    List<ExactDuplicateGroupKeyProjection> findGroupKeys(
            UUID actorUserId,
            UUID folderId,
            String mimeType,
            Pageable pageable);

    @Query("""
            select new com.filemanager.api.duplicate.persistence.ExactDuplicateGroupFileProjection(
                fingerprint.algorithm,
                fingerprint.hashValue,
                file.id,
                file.name,
                file.mimeType,
                file.size
            )
            from FileFingerprint fingerprint
            join fingerprint.file file
            left join file.folder folder
            where file.ownerUser.id = :actorUserId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
                and fingerprint.hashValue in :hashValues
                and (:folderId is null or folder.id = :folderId)
                and (:mimeType is null or file.mimeType = :mimeType)
            order by fingerprint.algorithm asc, fingerprint.hashValue asc, file.createdAt asc, file.id asc
            """)
    List<ExactDuplicateGroupFileProjection> findGroupFiles(
            UUID actorUserId,
            Collection<String> hashValues,
            UUID folderId,
            String mimeType);
}
