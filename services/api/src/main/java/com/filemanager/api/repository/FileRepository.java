package com.filemanager.api.repository;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.User;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID>, JpaSpecificationExecutor<FileEntity> {
    Optional<FileEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("select coalesce(sum(f.size), 0) from FileEntity f where f.ownerUser = :ownerUser and f.deletedAt is null")
    long sumActiveSizeByOwnerUser(User ownerUser);

    boolean existsByFolderAndDeletedAtIsNull(FolderEntity folder);
}
