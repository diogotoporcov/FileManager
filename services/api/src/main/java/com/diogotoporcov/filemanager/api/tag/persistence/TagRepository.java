package com.diogotoporcov.filemanager.api.tag.persistence;

import com.diogotoporcov.filemanager.api.tag.domain.TagEntity;
import com.diogotoporcov.filemanager.api.tag.domain.TagScopeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, UUID> {
    Optional<TagEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<TagEntity> findByOwnerUserIdAndScopeTypeAndNormalizedNameAndDeletedAtIsNull(
            UUID ownerUserId,
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
              and tag.scopeType = com.diogotoporcov.filemanager.api.tag.domain.TagScopeType.OWNER
              and (:query is null or tag.normalizedName like concat('%', :query, '%'))
            order by tag.normalizedName asc, tag.id asc
            """)
    List<TagEntity> listOwnerUserTags(UUID ownerUserId, String query, Pageable pageable);

    @Query("""
            select tag
            from TagEntity tag
            where tag.deletedAt is null
              and tag.scopeFolder.id = :scopeFolderId
              and tag.scopeType = com.diogotoporcov.filemanager.api.tag.domain.TagScopeType.FOLDER
              and (:query is null or tag.normalizedName like concat('%', :query, '%'))
            order by tag.normalizedName asc, tag.id asc
            """)
    List<TagEntity> listFolderTags(UUID scopeFolderId, String query, Pageable pageable);
}
