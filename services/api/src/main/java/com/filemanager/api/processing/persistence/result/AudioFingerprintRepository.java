package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.AudioFingerprint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioFingerprintRepository extends JpaRepository<AudioFingerprint, UUID> {
    Optional<AudioFingerprint> findByFileId(UUID fileId);
}
