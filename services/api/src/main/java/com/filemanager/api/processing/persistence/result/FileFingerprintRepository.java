package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import com.filemanager.api.processing.domain.result.FileFingerprint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileFingerprintRepository extends JpaRepository<FileFingerprint, UUID> {
    Optional<FileFingerprint> findByFileIdAndAlgorithm(UUID fileId, FingerprintAlgorithm algorithm);
}
