package com.filemanager.api.repository;

import com.filemanager.api.entity.FolderTagEntity;
import com.filemanager.api.entity.FolderTagId;
import com.filemanager.api.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
