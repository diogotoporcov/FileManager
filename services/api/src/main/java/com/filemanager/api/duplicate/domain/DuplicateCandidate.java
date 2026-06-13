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
@Table(name = "duplicate_candidates", indexes = {
    @Index(
            name = "idx_duplicate_candidates_owner_method_low",
            columnList = "owner_user_id, method, file_id_low"),
    @Index(
            name = "idx_duplicate_candidates_owner_method_high",
            columnList = "owner_user_id, method, file_id_high")
}, uniqueConstraints = {
    @UniqueConstraint(
            name = "uk_duplicate_candidates_pair_method_version",
            columnNames = {
                "owner_user_id",
                "file_id_low",
                "file_id_high",
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
public class DuplicateCandidate {
    public static final String NO_MODEL = "none";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "file_id_low", nullable = false)
    private UUID fileIdLow;

    @Column(name = "file_id_high", nullable = false)
    private UUID fileIdHigh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DuplicateSearchMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DuplicateConfidence confidence;

    @Column
    private Double distance;

    @Column(nullable = false)
    private Double score;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 64)
    private DuplicateEvidenceType evidenceType;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "threshold_version", nullable = false)
    private String thresholdVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DuplicateCandidateStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum DuplicateCandidateStatus {
        ACTIVE,
        STALE
    }
}
