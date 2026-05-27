package com.filemanager.api.repository;

import com.filemanager.api.entity.FileEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileEmbeddingRepository extends JpaRepository<FileEmbedding, UUID> {
    Optional<FileEmbedding> findByFileIdAndModelNameAndModelVersion(
            UUID fileId,
            String modelName,
            String modelVersion);
}
