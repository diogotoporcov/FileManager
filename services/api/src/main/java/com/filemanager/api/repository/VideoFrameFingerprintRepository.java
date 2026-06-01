package com.filemanager.api.repository;

import com.filemanager.api.entity.VideoFrameFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VideoFrameFingerprintRepository extends JpaRepository<VideoFrameFingerprint, UUID> {
    void deleteByFileId(UUID fileId);
}
