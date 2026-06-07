package com.filemanager.api.folder.persistence;

import com.filemanager.api.folder.domain.FolderClosureEntity;
import com.filemanager.api.folder.domain.FolderClosureId;
import com.filemanager.api.folder.domain.FolderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FolderClosureRepository extends JpaRepository<FolderClosureEntity, FolderClosureId> {
    List<FolderClosureEntity> findByDescendantFolderOrderByDepthAsc(FolderEntity descendantFolder);
}
