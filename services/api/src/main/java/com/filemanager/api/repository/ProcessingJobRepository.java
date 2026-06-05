package com.filemanager.api.repository;

import com.filemanager.api.entity.ProcessingJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    @Query("""
            select job
            from ProcessingJob job
            where job.file.id = :fileId
            order by job.createdAt asc, job.id asc
            """)
    List<ProcessingJob> findPage(@Param("fileId") UUID fileId, Pageable pageable);

    @Query("""
            select job
            from ProcessingJob job
            where job.file.id = :fileId
                and (job.createdAt > :createdAt
                    or (job.createdAt = :createdAt and job.id > :id))
            order by job.createdAt asc, job.id asc
            """)
    List<ProcessingJob> findPageAfterCursor(
            @Param("fileId") UUID fileId,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("id") UUID id,
            Pageable pageable);
}
