package com.diogotoporcov.filemanager.api.folder.domain;

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

@Entity
@Table(name = "folder_closure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderClosureEntity {
    @EmbeddedId
    private FolderClosureId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("ancestorFolderId")
    @JoinColumn(name = "ancestor_folder_id", nullable = false)
    private FolderEntity ancestorFolder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("descendantFolderId")
    @JoinColumn(name = "descendant_folder_id", nullable = false)
    private FolderEntity descendantFolder;

    @Column(nullable = false)
    private int depth;
}
