package com.diogotoporcov.filemanager.api.folder.persistence;

import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
    Optional<FolderEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select distinct folder
            from FolderEntity folder
            where folder.deletedAt is null
              and folder.parentFolder is null
              and (
                    folder.ownerUser.id = :actorUserId
                    or exists (
                        select grant.id
                        from FolderGrantEntity grant
                        where grant.folder = folder
                          and grant.granteeUser.id = :actorUserId
                          and grant.permission = com.diogotoporcov.filemanager.api.auth.domain.Permission.FOLDER_VIEW
                          and grant.revokedAt is null
                    )
              )
            order by folder.name asc, folder.id asc
            """)
    List<FolderEntity> findVisibleRootFolders(UUID actorUserId);

    @Query("""
            select distinct folder
            from FolderEntity folder, FolderTagEntity assignment
            where assignment.folder = folder
              and assignment.tag.id = :tagId
              and assignment.tag.deletedAt is null
              and folder.deletedAt is null
              and folder.parentFolder is null
              and (
                    folder.ownerUser.id = :actorUserId
                    or exists (
                        select grant.id
                        from FolderGrantEntity grant
                        where grant.folder = folder
                          and grant.granteeUser.id = :actorUserId
                          and grant.permission = com.diogotoporcov.filemanager.api.auth.domain.Permission.FOLDER_VIEW
                          and grant.revokedAt is null
                    )
              )
            order by folder.name asc, folder.id asc
            """)
    List<FolderEntity> findVisibleTaggedRootFolders(UUID actorUserId, UUID tagId);

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

    boolean existsByNameIgnoreCaseAndParentFolderAndDeletedAtIsNull(String name, FolderEntity parentFolder);

    boolean existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(String name, User ownerUser);

}
