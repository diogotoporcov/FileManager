package com.filemanager.api.repository;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {
    Optional<FileEntity> findByIdAndDeletedAtIsNull(UUID id);
    List<FileEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    @Query(value = """
            SELECT f.id AS id,
                   f.name AS name,
                   f.mime_type AS mimeType,
                   f.size AS size,
                   f.owner_user_id AS ownerUserId,
                   f.owner_organization_id AS ownerOrganizationId,
                   f.created_at AS createdAt,
                   f.updated_at AS updatedAt
            FROM files f
            WHERE f.owner_user_id = :ownerUserId
              AND f.deleted_at IS NULL
              AND (
                    CAST(:cursorCreatedAt AS timestamptz) IS NULL
                    OR f.created_at < CAST(:cursorCreatedAt AS timestamptz)
                    OR (f.created_at = CAST(:cursorCreatedAt AS timestamptz)
                        AND f.id < CAST(:cursorId AS uuid))
                  )
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<FileListItemProjection> findPageByOwnerUser(
            UUID ownerUserId,
            java.time.OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int maxRows);

    @Query(value = """
            SELECT f.id AS id,
                   f.name AS name,
                   f.mime_type AS mimeType,
                   f.size AS size,
                   f.owner_user_id AS ownerUserId,
                   f.owner_organization_id AS ownerOrganizationId,
                   f.created_at AS createdAt,
                   f.updated_at AS updatedAt
            FROM files f
            WHERE f.owner_organization_id = :ownerOrganizationId
              AND f.deleted_at IS NULL
              AND (
                    CAST(:cursorCreatedAt AS timestamptz) IS NULL
                    OR f.created_at < CAST(:cursorCreatedAt AS timestamptz)
                    OR (f.created_at = CAST(:cursorCreatedAt AS timestamptz)
                        AND f.id < CAST(:cursorId AS uuid))
                  )
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<FileListItemProjection> findPageByOwnerOrganization(
            UUID ownerOrganizationId,
            java.time.OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int maxRows);

    @Query("select coalesce(sum(f.size), 0) from FileEntity f where f.ownerUser = :ownerUser and f.deletedAt is null")
    long sumActiveSizeByOwnerUser(User ownerUser);

    @Query("select coalesce(sum(f.size), 0) from FileEntity f where f.ownerOrganization = :ownerOrganization and f.deletedAt is null")
    long sumActiveSizeByOwnerOrganization(Organization ownerOrganization);
}
