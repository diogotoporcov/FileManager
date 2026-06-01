package com.filemanager.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_fingerprints", indexes = {
    @Index(name = "idx_video_fingerprints_file", columnList = "file_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_video_fingerprints_file", columnNames = {"file_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoFingerprint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "frame_count")
    private Long frameCount;

    @Column
    private String codec;

    @Column(name = "sampled_frame_count", nullable = false)
    private Integer sampledFrameCount;

    @Column(name = "sampling_strategy", nullable = false)
    private String samplingStrategy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
