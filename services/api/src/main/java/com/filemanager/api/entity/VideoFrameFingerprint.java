package com.filemanager.api.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_frame_fingerprints", indexes = {
    @Index(name = "idx_video_frame_fingerprints_file", columnList = "file_id"),
    @Index(name = "idx_video_frame_fingerprints_phash", columnList = "phash")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_video_frame_fingerprints_file_frame", columnNames = {"file_id", "frame_index"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoFrameFingerprint {
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

    @Column(nullable = false)
    private String phash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
