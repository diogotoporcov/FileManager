package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.VideoFrameFingerprint;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoFrameFingerprintRepository extends JpaRepository<VideoFrameFingerprint, UUID> {
    void deleteByFileId(UUID fileId);
}
