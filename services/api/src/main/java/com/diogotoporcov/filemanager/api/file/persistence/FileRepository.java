package com.diogotoporcov.filemanager.api.file.persistence;

import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID>, JpaSpecificationExecutor<FileEntity> {
    Optional<FileEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select file
            from FileEntity file
            left join file.folder folder
            where file.id = :id
                and file.deletedAt is null
                and (folder is null or folder.deletedAt is null)
            """)
    Optional<FileEntity> findEligibleById(UUID id);

    @Query("select coalesce(sum(f.size), 0) from FileEntity f where f.ownerUser = :ownerUser and f.deletedAt is null")
    long sumActiveSizeByOwnerUser(User ownerUser);

    boolean existsByFolderAndDeletedAtIsNull(FolderEntity folder);
}
