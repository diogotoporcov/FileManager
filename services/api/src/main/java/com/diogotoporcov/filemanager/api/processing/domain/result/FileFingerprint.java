package com.diogotoporcov.filemanager.api.processing.domain.result;

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
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;

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
