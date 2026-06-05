package com.filemanager.api.sharing.persistence;

import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.sharing.domain.FolderGrantEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderGrantRepository extends JpaRepository<FolderGrantEntity, UUID> {
    Optional<FolderGrantEntity> findByFolderIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(
            UUID folderId,
            UUID granteeUserId,
            Permission permission);

    Optional<FolderGrantEntity> findByIdAndFolderIdAndRevokedAtIsNull(UUID id, UUID folderId);

    List<FolderGrantEntity> findByFolderIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(UUID folderId);

    @Query("""
            select count(grant) > 0
            from FolderGrantEntity grant
            where grant.folder.id = :folderId
              and grant.granteeUser.id = :granteeUserId
              and grant.permission = :permission
              and grant.revokedAt is null
              and grant.folder.deletedAt is null
            """)
    boolean hasActiveGrant(UUID folderId, UUID granteeUserId, Permission permission);
}
