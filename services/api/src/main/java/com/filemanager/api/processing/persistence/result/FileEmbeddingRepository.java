package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.FileEmbedding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileEmbeddingRepository extends JpaRepository<FileEmbedding, UUID> {
    Optional<FileEmbedding> findByFileIdAndModelNameAndModelVersion(
            UUID fileId,
            String modelName,
            String modelVersion);
}
