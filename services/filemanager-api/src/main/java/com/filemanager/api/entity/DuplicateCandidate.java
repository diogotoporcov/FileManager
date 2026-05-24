package com.filemanager.api.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "duplicate_candidates", indexes = {
    @Index(name = "idx_duplicate_source", columnList = "source_file_id"),
    @Index(name = "idx_duplicate_candidate", columnList = "candidate_file_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_duplicate_source_candidate_method", columnNames = {"source_file_id", "candidate_file_id", "detection_method"})
}, check = {
    @CheckConstraint(name = "chk_source_not_candidate", constraint = "source_file_id <> candidate_file_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuplicateCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private FileEntity sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_file_id", nullable = false)
    private FileEntity candidateFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_method", nullable = false)
    private DetectionMethod detectionMethod;

    private Double distance;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CandidateStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public enum DetectionMethod {
        EXACT,
        PHASH,
        EMBEDDING
    }

    public enum CandidateStatus {
        PENDING,
        CONFIRMED,
        REJECTED
    }
}
