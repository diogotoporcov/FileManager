package com.diogotoporcov.filemanager.api.duplicate.domain;

import com.diogotoporcov.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
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
@Table(name = "exact_duplicate_groups", indexes = {
    @Index(
            name = "idx_exact_duplicate_groups_owner_count_algorithm_hash",
            columnList = "owner_user_id, active_file_count, algorithm, hash_value")
}, uniqueConstraints = {
    @UniqueConstraint(
            name = "uk_exact_duplicate_groups_owner_algorithm_hash",
            columnNames = {"owner_user_id", "algorithm", "hash_value"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExactDuplicateGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FingerprintAlgorithm algorithm;

    @Column(name = "hash_value", nullable = false)
    private String hashValue;

    @Column(name = "active_file_count", nullable = false)
    private long activeFileCount;

    @Column(name = "representative_file_id")
    private UUID representativeFileId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
