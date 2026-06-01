package com.filemanager.api.repository;

import com.filemanager.api.entity.VideoFrameEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VideoFrameEmbeddingRepository extends JpaRepository<VideoFrameEmbedding, UUID> {
    void deleteByFileId(UUID fileId);
}
