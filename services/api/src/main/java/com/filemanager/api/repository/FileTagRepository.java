package com.filemanager.api.repository;

import com.filemanager.api.entity.FileTagEntity;
import com.filemanager.api.entity.FileTagId;
import com.filemanager.api.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileTagRepository extends JpaRepository<FileTagEntity, FileTagId> {
    @Query("""
            select assignment.tag
            from FileTagEntity assignment
            where assignment.file.id = :fileId
              and assignment.file.deletedAt is null
              and assignment.tag.deletedAt is null
            order by assignment.tag.normalizedName asc, assignment.tag.id asc
            """)
    List<TagEntity> findActiveTagsByFileId(UUID fileId);
}
