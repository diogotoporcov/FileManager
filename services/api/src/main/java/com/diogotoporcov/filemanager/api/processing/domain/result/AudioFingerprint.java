package com.diogotoporcov.filemanager.api.processing.domain.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "audio_fingerprints", indexes = {
    @Index(name = "idx_audio_fingerprints_file", columnList = "file_id"),
    @Index(name = "idx_audio_fingerprints_hash", columnList = "fingerprint_hash")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_audio_fingerprints_file", columnNames = {"file_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioFingerprint {
    public static final int MAX_FINGERPRINT_LENGTH = 32768;
    public static final int MAX_FINGERPRINT_ALGORITHM_LENGTH = 64;
    public static final int MAX_FINGERPRINT_VERSION_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(nullable = false)
    private String codec;

    @Column(name = "sample_rate", nullable = false)
    private Integer sampleRate;

    @Column(nullable = false)
    private Integer channels;

    @Column(name = "bit_rate")
    private Long bitRate;

    @Column(name = "audio_stream_index")
    private Integer audioStreamIndex;

    @Column(name = "container_format")
    private String containerFormat;

    @Column(nullable = false, length = MAX_FINGERPRINT_LENGTH)
    private String fingerprint;

    @Column(name = "fingerprint_hash", nullable = false, length = 64)
    private String fingerprintHash;

    @Column(name = "fingerprint_algorithm", nullable = false, length = MAX_FINGERPRINT_ALGORITHM_LENGTH)
    private String fingerprintAlgorithm;

    @Column(name = "fingerprint_version", nullable = false, length = MAX_FINGERPRINT_VERSION_LENGTH)
    private String fingerprintVersion;

    @Column(name = "fingerprint_duration_seconds", nullable = false)
    private Integer fingerprintDurationSeconds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void updateFingerprintHash() {
        if (fingerprint == null) {
            fingerprintHash = null;
            return;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            fingerprintHash = HexFormat.of().formatHex(digest.digest(fingerprint.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
