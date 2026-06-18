package com.filemanager.api.processing.persistence.result;

import com.filemanager.api.processing.domain.result.ImagePhashMihChunk;
import com.filemanager.api.processing.domain.result.ImagePhashMihChunkId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagePhashMihChunkRepository extends JpaRepository<ImagePhashMihChunk, ImagePhashMihChunkId> {
    @Modifying(flushAutomatically = true)
    @Query("delete from ImagePhashMihChunk chunk where chunk.id.fileId = :fileId")
    void deleteByIdFileId(UUID fileId);
}
