package com.filemanager.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_fingerprints", indexes = {
    @Index(name = "idx_fingerprints_value", columnList = "hash_value"),
    @Index(name = "idx_fingerprints_file_algo", columnList = "file_id, algorithm")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_fingerprints_file_algo", columnNames = {"file_id", "algorithm"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileFingerprint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FingerprintAlgorithm algorithm;

    @Column(name = "hash_value", nullable = false)
    private String hashValue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public enum FingerprintAlgorithm {
        SHA256
    }
}
