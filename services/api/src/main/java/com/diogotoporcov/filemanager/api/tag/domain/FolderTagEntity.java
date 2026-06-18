package com.diogotoporcov.filemanager.api.tag.domain;

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
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.identity.domain.User;

import java.time.OffsetDateTime;

@Entity
@Table(name = "folder_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderTagEntity {
    @EmbeddedId
    private FolderTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("folderId")
    @JoinColumn(name = "folder_id", nullable = false)
    private FolderEntity folder;

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
