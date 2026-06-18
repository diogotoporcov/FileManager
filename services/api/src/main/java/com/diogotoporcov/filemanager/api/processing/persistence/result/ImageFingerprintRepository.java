package com.diogotoporcov.filemanager.api.processing.persistence.result;

import com.diogotoporcov.filemanager.api.processing.domain.result.ImageFingerprint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageFingerprintRepository extends JpaRepository<ImageFingerprint, UUID> {
    Optional<ImageFingerprint> findByFileId(UUID fileId);
}
