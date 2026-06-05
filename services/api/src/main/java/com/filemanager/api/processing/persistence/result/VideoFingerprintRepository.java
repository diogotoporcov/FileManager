package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.VideoFingerprint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoFingerprintRepository extends JpaRepository<VideoFingerprint, UUID> {
    Optional<VideoFingerprint> findByFileId(UUID fileId);
}
