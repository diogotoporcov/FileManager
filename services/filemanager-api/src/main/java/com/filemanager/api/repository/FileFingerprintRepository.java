package com.filemanager.api.repository;

import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.FileFingerprint.FingerprintAlgorithm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileFingerprintRepository extends JpaRepository<FileFingerprint, UUID> {
    List<FileFingerprint> findByAlgorithmAndHashValue(FingerprintAlgorithm algorithm, String hashValue);
    Optional<FileFingerprint> findByFileIdAndAlgorithm(UUID fileId, FingerprintAlgorithm algorithm);
}
