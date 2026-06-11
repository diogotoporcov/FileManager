package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.VideoEmbedding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoEmbeddingRepository extends JpaRepository<VideoEmbedding, UUID> {
    Optional<VideoEmbedding> findByFileId(UUID fileId);

    void deleteByFileId(UUID fileId);
}
