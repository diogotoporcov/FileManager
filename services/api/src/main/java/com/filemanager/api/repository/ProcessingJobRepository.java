package com.filemanager.api.repository;

import com.filemanager.api.entity.ProcessingJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    List<ProcessingJob> findAllByFile_IdOrderByCreatedAtAsc(UUID fileId, Pageable pageable);
}
