package com.diogotoporcov.filemanager.api.tag.persistence;

import com.diogotoporcov.filemanager.api.tag.domain.FolderTagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.FolderTagId;
import com.diogotoporcov.filemanager.api.tag.domain.TagEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderTagRepository extends JpaRepository<FolderTagEntity, FolderTagId> {
    @Query("""
            select assignment.tag
            from FolderTagEntity assignment
            where assignment.folder.id = :folderId
              and assignment.folder.deletedAt is null
              and assignment.tag.deletedAt is null
            order by assignment.tag.normalizedName asc, assignment.tag.id asc
            """)
    List<TagEntity> findActiveTagsByFolderId(UUID folderId);
}
