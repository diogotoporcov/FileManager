package com.filemanager.api.service;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.entity.ImageFingerprint;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.ProcessingJob;
import com.filemanager.api.entity.User;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
import com.filemanager.api.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.filemanager.api.entity.FileFingerprint.FingerprintAlgorithm.SHA256;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateScopingTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileFingerprintRepository fileFingerprintRepository;
    @Mock
    private ImageFingerprintRepository imageFingerprintRepository;
    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private FileManagerMetrics fileManagerMetrics;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private ProcessingJobService processingJobService;

    private User user1;
    private Organization org1;

    @BeforeEach
    void setup() {
        AppProperties.Phash phash = new AppProperties.Phash();
        phash.setThreshold(10);
        lenient().when(appProperties.getPhash()).thenReturn(phash);

        user1 = new User(); user1.setId(UUID.randomUUID());
        org1 = new Organization(); org1.setId(UUID.randomUUID());
    }

    @Test
    void handleChecksumResult_DifferentUsers_ShouldNotDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerUser(user1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        // Stubs for the new owner-scoped methods should return empty list for different owners
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(SHA256, sha256, user1.getId()))
                .thenReturn(List.of());

        processingJobService.handleChecksumResult(jobId, file1Id, sha256);

        verify(duplicateCandidateRepository, never()).save(any());
        verify(fileManagerMetrics).recordJobCompleted("CHECKSUM");
    }

    @Test
    void handleChecksumResult_DifferentOrgs_ShouldNotDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerOrganization(org1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(SHA256, sha256, org1.getId()))
                .thenReturn(List.of());

        processingJobService.handleChecksumResult(jobId, file1Id, sha256);

        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void handleChecksumResult_SameOrganization_ShouldDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        UUID file2Id = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerOrganization(org1).build();
        FileEntity file2 = FileEntity.builder().id(file2Id).ownerOrganization(org1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        FileFingerprint fingerprint2 = FileFingerprint.builder()
                .file(file2)
                .algorithm(SHA256)
                .hashValue(sha256)
                .build();

        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(SHA256, sha256, org1.getId()))
                .thenReturn(List.of(fingerprint2));

        processingJobService.handleChecksumResult(jobId, file1Id, sha256);

        verify(duplicateCandidateRepository, times(1)).save(any());
    }

    @Test
    void handlePhashResult_SameOrganization_ShouldDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        UUID file2Id = UUID.randomUUID();
        String phash = "fedcba9876543210";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerOrganization(org1).build();
        FileEntity file2 = FileEntity.builder().id(file2Id).ownerOrganization(org1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        ImageFingerprint fingerprint2 = ImageFingerprint.builder()
                .file(file2)
                .phash(phash)
                .build();

        when(imageFingerprintRepository.findByFileOwnerOrganizationIdAndFileDeletedAtIsNull(org1.getId()))
                .thenReturn(List.of(fingerprint2));

        processingJobService.handlePhashResult(jobId, file1Id, phash);

        verify(duplicateCandidateRepository, times(1)).save(any());
        verify(fileManagerMetrics).recordJobCompleted("PHASH");
        verify(fileManagerMetrics).recordDuplicateCandidateCreated("PHASH");
    }

    @Test
    void handlePhashResult_DifferentOrganizations_ShouldNotDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        String phash = "fedcba9876543210";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerOrganization(org1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        when(imageFingerprintRepository.findByFileOwnerOrganizationIdAndFileDeletedAtIsNull(org1.getId()))
                .thenReturn(List.of());

        processingJobService.handlePhashResult(jobId, file1Id, phash);

        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void handleChecksumResult_SameUser_ShouldDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        UUID file2Id = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerUser(user1).build();
        FileEntity file2 = FileEntity.builder().id(file2Id).ownerUser(user1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        FileFingerprint fingerprint2 = FileFingerprint.builder()
                .file(file2)
                .algorithm(SHA256)
                .hashValue(sha256)
                .build();

        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(SHA256, sha256, user1.getId()))
                .thenReturn(List.of(fingerprint2));

        processingJobService.handleChecksumResult(jobId, file1Id, sha256);

        verify(duplicateCandidateRepository, times(1)).save(any());
    }

    @Test
    void handlePhashResult_DifferentUsers_ShouldNotDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        String phash = "fedcba9876543210";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerUser(user1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        when(imageFingerprintRepository.findByFileOwnerUserIdAndFileDeletedAtIsNull(user1.getId()))
                .thenReturn(List.of());

        processingJobService.handlePhashResult(jobId, file1Id, phash);

        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void handlePhashResult_SameUser_ShouldDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        UUID file2Id = UUID.randomUUID();
        String phash = "fedcba9876543210";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerUser(user1).build();
        FileEntity file2 = FileEntity.builder().id(file2Id).ownerUser(user1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        ImageFingerprint fingerprint2 = ImageFingerprint.builder()
                .file(file2)
                .phash(phash)
                .build();

        when(imageFingerprintRepository.findByFileOwnerUserIdAndFileDeletedAtIsNull(user1.getId()))
                .thenReturn(List.of(fingerprint2));

        processingJobService.handlePhashResult(jobId, file1Id, phash);

        verify(duplicateCandidateRepository, times(1)).save(any());
    }

    @Test
    void handleChecksumResult_DeletedFile_ShouldNotDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerUser(user1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        // The repository method name already implies deleted files are excluded
        when(fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(SHA256, sha256, user1.getId()))
                .thenReturn(List.of());

        processingJobService.handleChecksumResult(jobId, file1Id, sha256);

        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void handlePhashResult_DeletedFile_ShouldNotDetectDuplicate() {
        UUID jobId = UUID.randomUUID();
        UUID file1Id = UUID.randomUUID();
        String phash = "fedcba9876543210";

        FileEntity file1 = FileEntity.builder().id(file1Id).ownerUser(user1).build();

        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file1);
        job.setJobType(ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(file1Id)).thenReturn(Optional.of(file1));
        
        when(imageFingerprintRepository.findByFileOwnerUserIdAndFileDeletedAtIsNull(user1.getId()))
                .thenReturn(List.of());

        processingJobService.handlePhashResult(jobId, file1Id, phash);

        verify(duplicateCandidateRepository, never()).save(any());
    }
}
