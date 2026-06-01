package com.filemanager.api.repository;

import com.filemanager.api.entity.TagEntity;
import com.filemanager.api.entity.TagScopeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, UUID> {
    Optional<TagEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<TagEntity> findByOwnerUserIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
            UUID ownerUserId,
            TagScopeType scopeType,
            String normalizedName);

    Optional<TagEntity> findByOwnerOrganizationIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
            UUID ownerOrganizationId,
            TagScopeType scopeType,
            String normalizedName);

    Optional<TagEntity> findByScopeFolderIdAndNormalizedNameAndDeletedAtIsNull(
            UUID scopeFolderId,
            String normalizedName);

    @Query("""
            select tag
            from TagEntity tag
            where tag.deletedAt is null
              and tag.ownerUser.id = :ownerUserId
              and tag.scopeType = com.filemanager.api.entity.TagScopeType.OWNER
              and (:query is null or tag.normalizedName like concat('%', :query, '%'))
            order by tag.normalizedName asc, tag.id asc
            """)
    List<TagEntity> listOwnerUserTags(UUID ownerUserId, String query, Pageable pageable);

    @Query("""
            select tag
            from TagEntity tag
            where tag.deletedAt is null
              and tag.ownerOrganization.id = :ownerOrganizationId
              and tag.scopeType = com.filemanager.api.entity.TagScopeType.OWNER
              and (:query is null or tag.normalizedName like concat('%', :query, '%'))
            order by tag.normalizedName asc, tag.id asc
            """)
    List<TagEntity> listOwnerOrganizationTags(UUID ownerOrganizationId, String query, Pageable pageable);

    @Query("""
            select tag
            from TagEntity tag
            where tag.deletedAt is null
              and tag.scopeFolder.id = :scopeFolderId
              and tag.scopeType = com.filemanager.api.entity.TagScopeType.FOLDER
              and (:query is null or tag.normalizedName like concat('%', :query, '%'))
            order by tag.normalizedName asc, tag.id asc
            """)
    List<TagEntity> listFolderTags(UUID scopeFolderId, String query, Pageable pageable);
}
