package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.VideoFrameEmbedding;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoFrameEmbeddingRepository extends JpaRepository<VideoFrameEmbedding, UUID> {
    void deleteByFileId(UUID fileId);
}
