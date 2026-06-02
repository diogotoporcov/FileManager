package com.filemanager.api.repository;

import com.filemanager.api.entity.AudioFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AudioFingerprintRepository extends JpaRepository<AudioFingerprint, UUID> {
    Optional<AudioFingerprint> findByFileId(UUID fileId);
}
