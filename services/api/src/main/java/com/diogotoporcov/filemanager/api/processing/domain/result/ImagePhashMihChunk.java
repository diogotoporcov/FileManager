package com.diogotoporcov.filemanager.api.processing.domain.result;

import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "image_phash_mih_chunks", indexes = {
    @Index(name = "idx_image_phash_mih_chunks_lookup", columnList = "chunk_index, chunk_value, file_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImagePhashMihChunk {
    @EmbeddedId
    private ImagePhashMihChunkId id;

    @MapsId("fileId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "chunk_value", nullable = false)
    private Integer chunkValue;
}
