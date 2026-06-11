package com.filemanager.api.duplicate.persistence;

import com.filemanager.api.processing.domain.result.AudioFingerprint;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioDuplicateCandidateRepository extends JpaRepository<AudioFingerprint, UUID> {
    @Query("""
            select new com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection(
                file.id,
                file.name,
                file.mimeType,
                file.size,
                candidate.fingerprintAlgorithm,
                candidate.fingerprintVersion,
                candidate.fingerprintHash
            )
            from AudioFingerprint candidate
            join candidate.file file
            left join file.folder folder
            where candidate.fingerprintAlgorithm = :fingerprintAlgorithm
                and candidate.fingerprintVersion = :fingerprintVersion
                and candidate.fingerprintHash = :fingerprintHash
                and file.ownerUser.id = :actorUserId
                and file.id <> :sourceFileId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
                and lower(file.mimeType) like 'audio/%'
            order by file.createdAt desc, file.id
            """)
    List<AudioDuplicateCandidateProjection> findCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            String fingerprintAlgorithm,
            String fingerprintVersion,
            String fingerprintHash,
            Pageable pageable);

    @Query("""
            select new com.filemanager.api.duplicate.persistence.AudioDuplicateGroupKeyProjection(
                fingerprint.fingerprintAlgorithm,
                fingerprint.fingerprintVersion,
                fingerprint.fingerprintHash,
                count(file.id)
            )
            from AudioFingerprint fingerprint
            join fingerprint.file file
            left join file.folder folder
            where file.ownerUser.id = :actorUserId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
                and lower(file.mimeType) like 'audio/%'
                and (:folderId is null or folder.id = :folderId)
                and (:mimeType is null or file.mimeType = :mimeType)
            group by fingerprint.fingerprintAlgorithm, fingerprint.fingerprintVersion, fingerprint.fingerprintHash
            having count(file.id) > 1
            order by count(file.id) desc, fingerprint.fingerprintAlgorithm asc, fingerprint.fingerprintVersion asc
            """)
    List<AudioDuplicateGroupKeyProjection> findGroupKeys(
            UUID actorUserId,
            UUID folderId,
            String mimeType,
            Pageable pageable);

    @Query("""
            select new com.filemanager.api.duplicate.persistence.AudioDuplicateGroupFileProjection(
                fingerprint.fingerprintAlgorithm,
                fingerprint.fingerprintVersion,
                fingerprint.fingerprintHash,
                file.id,
                file.name,
                file.mimeType,
                file.size
            )
            from AudioFingerprint fingerprint
            join fingerprint.file file
            left join file.folder folder
            where file.ownerUser.id = :actorUserId
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
                and lower(file.mimeType) like 'audio/%'
                and fingerprint.fingerprintHash in :fingerprintHashes
                and (:folderId is null or folder.id = :folderId)
                and (:mimeType is null or file.mimeType = :mimeType)
            order by fingerprint.fingerprintAlgorithm asc, fingerprint.fingerprintVersion asc, file.createdAt asc, file.id asc
            """)
    List<AudioDuplicateGroupFileProjection> findGroupFiles(
            UUID actorUserId,
            Collection<String> fingerprintHashes,
            UUID folderId,
            String mimeType);
}
