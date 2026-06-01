package com.filemanager.api.entity;

import com.filemanager.api.config.EmbeddingDimensions;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_frame_embeddings", indexes = {
    @Index(name = "idx_video_frame_embeddings_file_model", columnList = "file_id, model_name, model_version")
}, uniqueConstraints = {
    @UniqueConstraint(
            name = "uk_video_frame_embeddings_file_frame_model",
            columnNames = {"file_id", "frame_index", "model_name", "model_version"})
}, check = {
    @CheckConstraint(
            name = "chk_video_frame_embedding_dimension_768",
            constraint = EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION_CHECK)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoFrameEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "timestamp_ms", nullable = false)
    private Long timestampMs;

    @Column(name = "frame_index", nullable = false)
    private Integer frameIndex;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
