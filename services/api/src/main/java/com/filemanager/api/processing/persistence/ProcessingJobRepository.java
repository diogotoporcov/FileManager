package com.filemanager.api.processing.persistence;

import com.filemanager.api.processing.domain.ProcessingJob;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
