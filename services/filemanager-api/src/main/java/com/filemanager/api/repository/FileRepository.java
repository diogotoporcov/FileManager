package com.filemanager.api.repository;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {
    List<FileEntity> findAllByOwnerUserAndDeletedAtIsNull(User ownerUser);
    List<FileEntity> findAllByOwnerOrganizationAndDeletedAtIsNull(Organization ownerOrganization);
    Optional<FileEntity> findByIdAndDeletedAtIsNull(UUID id);
}
