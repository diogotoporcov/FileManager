package com.filemanager.api.tag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.identity.domain.User;

import java.time.OffsetDateTime;

@Entity
@Table(name = "file_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTagEntity {
    @EmbeddedId
    private FileTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("fileId")
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", nullable = false)
    private TagEntity tag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tagged_by_user_id", nullable = false)
    private User taggedByUser;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
