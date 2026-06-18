package com.diogotoporcov.filemanager.api.processing.domain.result;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ImagePhashMihChunkId implements Serializable {
    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "chunk_index", nullable = false)
    private Short chunkIndex;
}
