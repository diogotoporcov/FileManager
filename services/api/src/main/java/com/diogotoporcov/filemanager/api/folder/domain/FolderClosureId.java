package com.diogotoporcov.filemanager.api.folder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FolderClosureId implements Serializable {
    @Column(name = "ancestor_folder_id", nullable = false)
    private UUID ancestorFolderId;

    @Column(name = "descendant_folder_id", nullable = false)
    private UUID descendantFolderId;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof FolderClosureId that)) {
            return false;
        }

        return Objects.equals(ancestorFolderId, that.ancestorFolderId)
                && Objects.equals(descendantFolderId, that.descendantFolderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ancestorFolderId, descendantFolderId);
    }
}
