package com.filemanager.api.processing.domain.result;

import com.filemanager.api.config.EmbeddingDimensions;
import com.filemanager.api.file.domain.FileEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "video_embeddings", indexes = {
    @Index(name = "idx_video_embeddings_model_version_dimension", columnList = "model_name, model_version, dimension")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_video_embeddings_file", columnNames = {"file_id"})
}, check = {
    @CheckConstraint(
            name = "chk_video_embedding_dimension_768",
            constraint = EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION_CHECK),
    @CheckConstraint(
            name = "chk_video_embedding_source_frame_count_positive",
            constraint = "source_frame_count > 0")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private Integer dimension;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "pooling_strategy", nullable = false)
    private String poolingStrategy;

    @Column(name = "source_frame_count", nullable = false)
    private Integer sourceFrameCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
