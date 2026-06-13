package com.filemanager.api.duplicate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "duplicate_candidate_refreshes", indexes = {
    @Index(
            name = "idx_duplicate_candidate_refreshes_source_method",
            columnList = "owner_user_id, source_file_id, method"),
    @Index(
            name = "idx_duplicate_candidate_refreshes_method_refreshed",
            columnList = "owner_user_id, method, refreshed_at")
}, uniqueConstraints = {
    @UniqueConstraint(
            name = "uk_duplicate_candidate_refreshes_source_method_version",
            columnNames = {
                "owner_user_id",
                "source_file_id",
                "method",
                "model_name",
                "model_version",
                "threshold_version"
            })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuplicateCandidateRefresh {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DuplicateSearchMethod method;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "threshold_version", nullable = false)
    private String thresholdVersion;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "refreshed_at", nullable = false)
    private OffsetDateTime refreshedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
