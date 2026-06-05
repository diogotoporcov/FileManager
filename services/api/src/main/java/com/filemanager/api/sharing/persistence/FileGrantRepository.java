package com.filemanager.api.sharing.persistence;

import com.filemanager.api.auth.domain.Permission;
import com.filemanager.api.sharing.domain.FileGrantEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FileGrantRepository extends JpaRepository<FileGrantEntity, UUID> {
    Optional<FileGrantEntity> findByFileIdAndGranteeUserIdAndPermissionAndRevokedAtIsNull(
            UUID fileId,
            UUID granteeUserId,
            Permission permission);

    Optional<FileGrantEntity> findByIdAndFileIdAndRevokedAtIsNull(UUID id, UUID fileId);

    List<FileGrantEntity> findByFileIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(UUID fileId);

    @Query("""
            select count(grant) > 0
            from FileGrantEntity grant
            where grant.file.id = :fileId
              and grant.granteeUser.id = :granteeUserId
              and grant.permission = :permission
              and grant.revokedAt is null
              and grant.file.deletedAt is null
            """)
    boolean hasActiveGrant(UUID fileId, UUID granteeUserId, Permission permission);
}
