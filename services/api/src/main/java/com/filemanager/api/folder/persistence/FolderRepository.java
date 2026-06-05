package com.filemanager.api.folder.persistence;

import com.filemanager.api.folder.domain.FolderEntity;
import com.filemanager.api.identity.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
    Optional<FolderEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<FolderEntity> findByOwnerUserAndParentFolderIsNullAndDeletedAtIsNullOrderByNameAsc(User ownerUser);

    @Query("""
            select folder
            from FolderEntity folder, FolderTagEntity assignment
            where assignment.folder = folder
              and assignment.tag.id = :tagId
              and assignment.tag.deletedAt is null
              and folder.ownerUser = :ownerUser
              and folder.parentFolder is null
              and folder.deletedAt is null
            order by folder.name asc, folder.id asc
            """)
    List<FolderEntity> findTaggedRootFoldersByOwnerUser(User ownerUser, UUID tagId);

    List<FolderEntity> findByParentFolderAndDeletedAtIsNullOrderByNameAsc(FolderEntity parentFolder);

    @Query("""
            select folder
            from FolderEntity folder, FolderTagEntity assignment
            where assignment.folder = folder
              and assignment.tag.id = :tagId
              and assignment.tag.deletedAt is null
              and folder.parentFolder = :parentFolder
              and folder.deletedAt is null
            order by folder.name asc, folder.id asc
            """)
    List<FolderEntity> findTaggedChildFolders(FolderEntity parentFolder, UUID tagId);

    boolean existsByParentFolderAndDeletedAtIsNull(FolderEntity parentFolder);

    boolean existsByNameIgnoreCaseAndOwnerUserAndParentFolderAndDeletedAtIsNull(
            String name,
            User ownerUser,
            FolderEntity parentFolder);

    boolean existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(String name, User ownerUser);

}
