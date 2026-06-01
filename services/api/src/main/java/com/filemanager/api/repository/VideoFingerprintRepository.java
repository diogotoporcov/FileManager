package com.filemanager.api.repository;

import com.filemanager.api.entity.VideoFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoFingerprintRepository extends JpaRepository<VideoFingerprint, UUID> {
    Optional<VideoFingerprint> findByFileId(UUID fileId);
}
