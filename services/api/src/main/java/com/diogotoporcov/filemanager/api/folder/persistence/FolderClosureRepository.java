package com.diogotoporcov.filemanager.api.folder.persistence;

import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureEntity;
import com.diogotoporcov.filemanager.api.folder.domain.FolderClosureId;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderClosureRepository extends JpaRepository<FolderClosureEntity, FolderClosureId> {
    List<FolderClosureEntity> findByDescendantFolderOrderByDepthAsc(FolderEntity descendantFolder);
}
