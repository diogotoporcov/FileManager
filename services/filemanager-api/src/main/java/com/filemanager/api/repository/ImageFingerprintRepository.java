package com.filemanager.api.repository;

import com.filemanager.api.entity.ImageFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageFingerprintRepository extends JpaRepository<ImageFingerprint, UUID> {
    Optional<ImageFingerprint> findByFileId(UUID fileId);
}
