package com.filemanager.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_embeddings", indexes = {
    @Index(name = "idx_embeddings_file_model", columnList = "file_id, model_name, model_version")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_embeddings_file_model", columnNames = {"file_id", "model_name", "model_version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Check(constraints = "dimension = 768")
public class FileEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private Integer dimension;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768) // Fixed to 768 for the current model
    @Column(nullable = false)
    private float[] embedding;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
