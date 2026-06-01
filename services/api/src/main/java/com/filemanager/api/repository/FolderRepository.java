package com.filemanager.api.repository;

import com.filemanager.api.entity.FolderEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<FolderEntity, UUID> {
    Optional<FolderEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<FolderEntity> findByOwnerUserAndParentFolderIsNullAndDeletedAtIsNullOrderByNameAsc(User ownerUser);

    List<FolderEntity> findByOwnerOrganizationAndParentFolderIsNullAndDeletedAtIsNullOrderByNameAsc(
            Organization ownerOrganization);

    List<FolderEntity> findByParentFolderAndDeletedAtIsNullOrderByNameAsc(FolderEntity parentFolder);

    boolean existsByParentFolderAndDeletedAtIsNull(FolderEntity parentFolder);

    boolean existsByNameIgnoreCaseAndOwnerUserAndParentFolderAndDeletedAtIsNull(
            String name,
            User ownerUser,
            FolderEntity parentFolder);

    boolean existsByNameIgnoreCaseAndOwnerUserAndParentFolderIsNullAndDeletedAtIsNull(String name, User ownerUser);

    boolean existsByNameIgnoreCaseAndOwnerOrganizationAndParentFolderAndDeletedAtIsNull(
            String name,
            Organization ownerOrganization,
            FolderEntity parentFolder);

    boolean existsByNameIgnoreCaseAndOwnerOrganizationAndParentFolderIsNullAndDeletedAtIsNull(
            String name,
            Organization ownerOrganization);
}
