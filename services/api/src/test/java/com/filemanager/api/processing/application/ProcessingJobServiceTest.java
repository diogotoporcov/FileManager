package com.filemanager.api.processing.application;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.config.EmbeddingDimensions;
import com.filemanager.api.duplicate.application.DuplicateCandidateMaintenanceService;
import com.filemanager.api.duplicate.application.ExactDuplicateGroupMaintenanceService;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.identity.domain.User;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.web.result.AudioAnalysisResultRequest;
import com.filemanager.api.processing.web.result.VideoAnalysisResultRequest;
import com.filemanager.api.processing.domain.ProcessingJob;
import com.filemanager.api.processing.domain.result.AudioFingerprint;
import com.filemanager.api.processing.domain.result.FileEmbedding;
import com.filemanager.api.processing.domain.result.FileFingerprint;
import com.filemanager.api.processing.domain.result.ImageFingerprint;
import com.filemanager.api.processing.domain.result.VideoEmbedding;
import com.filemanager.api.processing.domain.result.VideoFingerprint;
import com.filemanager.api.processing.domain.result.VideoFrameEmbedding;
import com.filemanager.api.processing.domain.result.VideoFrameFingerprint;
import com.filemanager.api.processing.persistence.ProcessingJobRepository;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import com.filemanager.api.processing.persistence.result.VideoEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.VideoFingerprintRepository;
import com.filemanager.api.processing.persistence.result.VideoFrameEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.VideoFrameFingerprintRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileFingerprintRepository fileFingerprintRepository;
    @Mock
    private ImageFingerprintRepository imageFingerprintRepository;
    @Mock
    private FileEmbeddingRepository fileEmbeddingRepository;
    @Mock
    private AudioFingerprintRepository audioFingerprintRepository;
    @Mock
    private VideoEmbeddingRepository videoEmbeddingRepository;
    @Mock
    private VideoFingerprintRepository videoFingerprintRepository;
    @Mock
    private VideoFrameFingerprintRepository videoFrameFingerprintRepository;
    @Mock
    private VideoFrameEmbeddingRepository videoFrameEmbeddingRepository;
    @Mock
    private FileManagerMetrics fileManagerMetrics;
    @Mock
    private AppProperties appProperties;
    @Mock
    private ExactDuplicateGroupMaintenanceService exactDuplicateGroupMaintenanceService;
    @Mock
    private DuplicateCandidateMaintenanceService duplicateCandidateMaintenanceService;

    @Captor
    private ArgumentCaptor<List<VideoFrameFingerprint>> frameFingerprintsCaptor;

    @Captor
    private ArgumentCaptor<List<VideoFrameEmbedding>> frameEmbeddingsCaptor;

    @Captor
    private ArgumentCaptor<VideoEmbedding> videoEmbeddingCaptor;

    @InjectMocks
    private ProcessingJobService processingJobService;

    private User owner;

    @BeforeEach
    void setup() {
        AppProperties.Embedding embedding = new AppProperties.Embedding();
        embedding.setModelName("openai/clip-vit-large-patch14");
        embedding.setModelVersion("1");
        embedding.setDimension(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        embedding.setSimilarityThreshold(0.20);
        embedding.setMaxCandidates(5000);
        lenient().when(appProperties.getEmbedding()).thenReturn(embedding);

        owner = User.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void handleChecksumResult_PersistsFingerprintAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String sha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(fileId, FileFingerprint.FingerprintAlgorithm.SHA256))
                .thenReturn(Optional.empty());

        processingJobService.handleChecksumResult(jobId, fileId, sha256);

        ArgumentCaptor<FileFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(FileFingerprint.class);
        verify(fileFingerprintRepository).save(fingerprintCaptor.capture());
        assertThat(fingerprintCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(fingerprintCaptor.getValue().getHashValue()).isEqualTo(sha256.toLowerCase());
        verify(exactDuplicateGroupMaintenanceService).refreshAfterFingerprintChange(
                owner.getId(),
                FileFingerprint.FingerprintAlgorithm.SHA256,
                null,
                sha256.toLowerCase());
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(fileManagerMetrics).recordJobCompleted("CHECKSUM");
    }

    @Test
    void handleChecksumResult_DoesNotCompleteJobWhenReadModelMaintenanceFails() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileFingerprintRepository.findByFileIdAndAlgorithm(fileId, FileFingerprint.FingerprintAlgorithm.SHA256))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("summary update failed"))
                .when(exactDuplicateGroupMaintenanceService)
                .refreshAfterFingerprintChange(owner.getId(), FileFingerprint.FingerprintAlgorithm.SHA256, null, sha256);

        assertThrows(
                IllegalStateException.class,
                () -> processingJobService.handleChecksumResult(jobId, fileId, sha256));

        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.PENDING);
        verify(processingJobRepository, never()).save(job);
        verify(fileManagerMetrics, never()).recordJobCompleted("CHECKSUM");
    }

    @Test
    void handlePhashResult_PersistsFingerprintAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.PHASH);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(imageFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        processingJobService.handlePhashResult(jobId, fileId, "FEDCBA9876543210");

        ArgumentCaptor<ImageFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(ImageFingerprint.class);
        verify(imageFingerprintRepository).save(fingerprintCaptor.capture());
        assertThat(fingerprintCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(fingerprintCaptor.getValue().getPhash()).isEqualTo("fedcba9876543210");
        verify(duplicateCandidateMaintenanceService).refreshImagePhashCandidates(file);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(fileManagerMetrics).recordJobCompleted("PHASH");
    }

    @Test
    void handleEmbeddingResult_PersistsEmbeddingAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.EMBEDDING);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                fileId,
                "openai/clip-vit-large-patch14",
                "1"))
                .thenReturn(Optional.empty());

        processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION,
                embeddingVector(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION));

        ArgumentCaptor<FileEmbedding> embeddingCaptor = ArgumentCaptor.forClass(FileEmbedding.class);
        verify(fileEmbeddingRepository).save(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(embeddingCaptor.getValue().getEmbedding()).hasSize(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        verify(duplicateCandidateMaintenanceService).refreshImageEmbeddingCandidates(
                file,
                "openai/clip-vit-large-patch14",
                "1",
                EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(fileManagerMetrics).recordJobCompleted("EMBEDDING");
    }

    @Test
    void handleEmbeddingResult_UpdatesExistingEmbedding() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.EMBEDDING);
        FileEmbedding existing = FileEmbedding.builder()
                .file(file)
                .modelName("openai/clip-vit-large-patch14")
                .modelVersion("1")
                .dimension(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION)
                .embedding(new float[EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION])
                .build();

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                fileId,
                "openai/clip-vit-large-patch14",
                "1"))
                .thenReturn(Optional.of(existing));

        processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION,
                embeddingVector(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION));

        verify(fileEmbeddingRepository).save(existing);
        assertThat(existing.getEmbedding()).hasSize(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
    }

    @Test
    void handleVideoAnalysisResult_PersistsMetadataFramesAndEmbeddings() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.VIDEO_ANALYSIS);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(videoFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());
        when(videoEmbeddingRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        processingJobService.handleVideoAnalysisResult(jobId, request);

        ArgumentCaptor<VideoFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(VideoFingerprint.class);

        verify(videoFingerprintRepository).save(fingerprintCaptor.capture());
        assertThat(fingerprintCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(fingerprintCaptor.getValue().getDurationMs()).isEqualTo(12_000L);
        assertThat(fingerprintCaptor.getValue().getSampledFrameCount()).isEqualTo(2);

        verify(videoFrameFingerprintRepository).deleteByFileId(fileId);
        verify(videoFrameEmbeddingRepository).deleteByFileId(fileId);
        verify(videoEmbeddingRepository).deleteByFileId(fileId);
        verify(videoFrameFingerprintRepository).saveAll(frameFingerprintsCaptor.capture());
        verify(videoFrameEmbeddingRepository).saveAll(frameEmbeddingsCaptor.capture());
        verify(videoEmbeddingRepository).save(videoEmbeddingCaptor.capture());

        assertThat(frameFingerprintsCaptor.getValue()).hasSize(2);
        assertThat(frameFingerprintsCaptor.getValue().getFirst().getPhash()).isEqualTo("fedcba9876543210");
        assertThat(frameEmbeddingsCaptor.getValue()).hasSize(2);
        assertThat(frameEmbeddingsCaptor.getValue().getFirst().getEmbedding())
                .hasSize(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        assertThat(videoEmbeddingCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(videoEmbeddingCaptor.getValue().getEmbedding()).hasSize(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        assertThat(videoEmbeddingCaptor.getValue().getPoolingStrategy()).isEqualTo("mean");
        assertThat(videoEmbeddingCaptor.getValue().getSourceFrameCount()).isEqualTo(2);
        verify(duplicateCandidateMaintenanceService).refreshVideoEmbeddingCandidates(
                file,
                "openai/clip-vit-large-patch14",
                "1",
                EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(fileManagerMetrics).recordJobCompleted("VIDEO_ANALYSIS");
    }

    @Test
    void handleVideoAnalysisResult_PersistsFramePhashesWithoutEmbeddings() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.VIDEO_ANALYSIS);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.setModelName(null);
        request.setModelVersion(null);
        request.setDimension(null);
        request.getFrames().forEach(frame -> frame.setEmbedding(null));

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(videoFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        processingJobService.handleVideoAnalysisResult(jobId, request);

        verify(videoFrameFingerprintRepository).saveAll(frameFingerprintsCaptor.capture());
        assertThat(frameFingerprintsCaptor.getValue()).hasSize(2);
        verify(videoFrameEmbeddingRepository).deleteByFileId(fileId);
        verify(videoEmbeddingRepository).deleteByFileId(fileId);
        verify(videoFrameEmbeddingRepository, never()).saveAll(any());
        verify(videoEmbeddingRepository, never()).save(any());
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
    }

    @Test
    void handleVideoAnalysisResult_PersistsFrameEmbeddingsWithoutPhashes() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.VIDEO_ANALYSIS);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.getFrames().forEach(frame -> frame.setPhash(null));

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(videoFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());
        when(videoEmbeddingRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        processingJobService.handleVideoAnalysisResult(jobId, request);

        verify(videoFrameEmbeddingRepository).saveAll(frameEmbeddingsCaptor.capture());
        assertThat(frameEmbeddingsCaptor.getValue()).hasSize(2);
        verify(videoEmbeddingRepository).save(videoEmbeddingCaptor.capture());
        assertThat(videoEmbeddingCaptor.getValue().getSourceFrameCount()).isEqualTo(2);
        verify(videoFrameFingerprintRepository).deleteByFileId(fileId);
        verify(videoFrameFingerprintRepository, never()).saveAll(any());
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
    }

    @Test
    void handleVideoAnalysisResult_ReplacesExistingPooledVideoEmbedding() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.VIDEO_ANALYSIS);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        VideoEmbedding existing = VideoEmbedding.builder()
                .file(file)
                .modelName("old")
                .modelVersion("old")
                .dimension(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION)
                .embedding(new float[EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION])
                .poolingStrategy("mean")
                .sourceFrameCount(1)
                .build();

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(videoFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());
        when(videoEmbeddingRepository.findByFileId(fileId)).thenReturn(Optional.of(existing));

        processingJobService.handleVideoAnalysisResult(jobId, request);

        verify(videoEmbeddingRepository).save(existing);
        assertThat(existing.getModelName()).isEqualTo("openai/clip-vit-large-patch14");
        assertThat(existing.getModelVersion()).isEqualTo("1");
        assertThat(existing.getSourceFrameCount()).isEqualTo(2);
        assertThat(existing.getEmbedding()).hasSize(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION);
    }

    @Test
    void handleAudioAnalysisResult_PersistsFingerprintAndCompletesJob() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.AUDIO_ANALYSIS);
        AudioAnalysisResultRequest request = audioAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(audioFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.empty());

        processingJobService.handleAudioAnalysisResult(jobId, request);

        ArgumentCaptor<AudioFingerprint> fingerprintCaptor = ArgumentCaptor.forClass(AudioFingerprint.class);
        verify(audioFingerprintRepository).save(fingerprintCaptor.capture());
        assertThat(fingerprintCaptor.getValue().getFile()).isEqualTo(file);
        assertThat(fingerprintCaptor.getValue().getDurationMs()).isEqualTo(12_000L);
        assertThat(fingerprintCaptor.getValue().getCodec()).isEqualTo("mp3");
        assertThat(fingerprintCaptor.getValue().getSampleRate()).isEqualTo(44_100);
        assertThat(fingerprintCaptor.getValue().getChannels()).isEqualTo(2);
        assertThat(fingerprintCaptor.getValue().getFingerprint()).isEqualTo("12345ABC");
        assertThat(fingerprintCaptor.getValue().getFingerprintAlgorithm()).isEqualTo("chromaprint");
        verify(duplicateCandidateMaintenanceService).refreshAudioFingerprintCandidates(file);
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
        verify(fileManagerMetrics).recordJobCompleted("AUDIO_ANALYSIS");
    }

    @Test
    void handleAudioAnalysisResult_UpdatesExistingFingerprint() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.AUDIO_ANALYSIS);
        AudioFingerprint existing = AudioFingerprint.builder()
                .file(file)
                .durationMs(1_000L)
                .codec("aac")
                .sampleRate(22_050)
                .channels(1)
                .fingerprint("old")
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion("old")
                .fingerprintDurationSeconds(10)
                .build();

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));
        when(audioFingerprintRepository.findByFileId(fileId)).thenReturn(Optional.of(existing));

        processingJobService.handleAudioAnalysisResult(jobId, audioAnalysisRequest(fileId));

        verify(audioFingerprintRepository).save(existing);
        assertThat(existing.getDurationMs()).isEqualTo(12_000L);
        assertThat(existing.getFingerprint()).isEqualTo("12345ABC");
        assertThat(existing.getFingerprintVersion()).isEqualTo("fpcalc-test");
        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.COMPLETED);
    }

    @Test
    void handleAudioAnalysisResult_RejectsWrongJobType() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(fileId), ProcessingJob.JobType.VIDEO_ANALYSIS);
        AudioAnalysisResultRequest request = audioAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleAudioAnalysisResult(jobId, request));
    }

    @Test
    void handleAudioAnalysisResult_RejectsJobFileMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(otherFileId), ProcessingJob.JobType.AUDIO_ANALYSIS);
        AudioAnalysisResultRequest request = audioAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleAudioAnalysisResult(jobId, request));
    }

    @Test
    void handleAudioAnalysisResult_RejectsDeletedFile() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(fileId), ProcessingJob.JobType.AUDIO_ANALYSIS);
        AudioAnalysisResultRequest request = audioAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> processingJobService.handleAudioAnalysisResult(jobId, request));
    }

    @Test
    void handleAudioAnalysisResult_RejectsInvalidFingerprintPayload() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AudioAnalysisResultRequest request = audioAnalysisRequest(fileId);
        request.setFingerprint(" ");

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleAudioAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsRequestDimensionMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.setDimension(512);
        request.getFrames().forEach(frame -> frame.setEmbedding(embeddingVector(512)));

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsFrameEmbeddingLengthMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.getFrames().getFirst().setEmbedding(embeddingVector(512));

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsModelNameMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.setModelName("other-model");

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsModelVersionMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.setModelVersion("2");

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsNonFiniteEmbeddingValue() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.getFrames().getFirst().getEmbedding().set(0, Double.NaN);

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsFramesWithoutSignals() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.setModelName(null);
        request.setModelVersion(null);
        request.setDimension(null);
        request.getFrames().forEach(frame -> {
            frame.setPhash(null);
            frame.setEmbedding(null);
        });

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsSampledFrameCountMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);
        request.setSampledFrameCount(1);

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsWrongJobType() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.EMBEDDING);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsJobFileMismatch() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID otherFileId = UUID.randomUUID();
        ProcessingJob job = job(jobId, file(otherFileId), ProcessingJob.JobType.VIDEO_ANALYSIS);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleVideoAnalysisResult_RejectsDeletedFile() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.VIDEO_ANALYSIS);
        VideoAnalysisResultRequest request = videoAnalysisRequest(fileId);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> processingJobService.handleVideoAnalysisResult(jobId, request));
    }

    @Test
    void handleProcessingFailure_MarksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileEntity file = file(fileId);
        ProcessingJob job = job(jobId, file, ProcessingJob.JobType.CHECKSUM);

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        processingJobService.handleProcessingFailure(jobId, fileId, "error");

        assertThat(job.getStatus()).isEqualTo(ProcessingJob.JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("error");
        verify(fileManagerMetrics).recordJobFailed("CHECKSUM");
    }

    @Test
    void handleEmbeddingResult_RejectsWrongDimension() {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> processingJobService.handleEmbeddingResult(
                jobId,
                fileId,
                "openai/clip-vit-large-patch14",
                "1",
                512,
                embeddingVector(512)));
    }

    private FileEntity file(UUID fileId) {
        return FileEntity.builder()
                .id(fileId)
                .name("file.jpg")
                .mimeType("image/jpeg")
                .size(10L)
                .ownerUser(owner)
                .build();
    }

    private ProcessingJob job(UUID jobId, FileEntity file, ProcessingJob.JobType jobType) {
        ProcessingJob job = new ProcessingJob();
        job.setId(jobId);
        job.setFile(file);
        job.setJobType(jobType);
        job.setStatus(ProcessingJob.JobStatus.PENDING);

        return job;
    }

    private List<Double> embeddingVector(int dimension) {
        return new java.util.ArrayList<>(java.util.stream.Stream.generate(() -> 1.0)
                .limit(dimension)
                .toList());
    }

    private VideoAnalysisResultRequest videoAnalysisRequest(UUID fileId) {
        return VideoAnalysisResultRequest.builder()
                .fileId(fileId)
                .durationMs(12_000L)
                .width(640)
                .height(360)
                .frameCount(360L)
                .codec("h264")
                .sampledFrameCount(2)
                .samplingStrategy("even_interval:min=2,max=32,target_seconds=10")
                .modelName("openai/clip-vit-large-patch14")
                .modelVersion("1")
                .dimension(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION)
                .frames(List.of(
                        VideoAnalysisResultRequest.FrameResult.builder()
                                .timestampMs(500L)
                                .frameIndex(0)
                                .phash("FEDCBA9876543210")
                                .embedding(embeddingVector(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION))
                                .build(),
                        VideoAnalysisResultRequest.FrameResult.builder()
                                .timestampMs(11_500L)
                                .frameIndex(1)
                                .phash("0123456789abcdef")
                                .embedding(embeddingVector(EmbeddingDimensions.IMAGE_EMBEDDING_DIMENSION))
                                .build()))
                .build();
    }

    private AudioAnalysisResultRequest audioAnalysisRequest(UUID fileId) {
        return AudioAnalysisResultRequest.builder()
                .fileId(fileId)
                .durationMs(12_000L)
                .codec("mp3")
                .sampleRate(44_100)
                .channels(2)
                .bitRate(128_000L)
                .audioStreamIndex(0)
                .containerFormat("mp3")
                .fingerprint("12345ABC")
                .fingerprintAlgorithm("chromaprint")
                .fingerprintVersion("fpcalc-test")
                .fingerprintDurationSeconds(60)
                .build();
    }
}
