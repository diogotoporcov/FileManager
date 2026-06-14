package com.filemanager.api.processing.web;

import com.filemanager.api.processing.web.result.AudioAnalysisResultRequest;
import com.filemanager.api.processing.web.result.ChecksumResultRequest;
import com.filemanager.api.processing.web.result.EmbeddingResultRequest;
import com.filemanager.api.processing.web.result.PhashResultRequest;
import com.filemanager.api.processing.web.result.ProcessingFailureRequest;
import com.filemanager.api.processing.application.ProcessingJobService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/processing/jobs")
@RequiredArgsConstructor
@Hidden
public class InternalProcessingController {

    private final ProcessingJobService processingJobService;

    @PostMapping("/{jobId}/checksum-result")
    public ResponseEntity<Void> reportChecksumResult(
            @PathVariable UUID jobId,
            @RequestBody @Valid ChecksumResultRequest request) {
        processingJobService.handleChecksumResult(jobId, request.getFileId(), request.getSha256());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{jobId}/phash-result")
    public ResponseEntity<Void> reportPhashResult(
            @PathVariable UUID jobId,
            @RequestBody @Valid PhashResultRequest request) {
        processingJobService.handlePhashResult(jobId, request.getFileId(), request.getPhash());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{jobId}/embedding-result")
    public ResponseEntity<Void> reportEmbeddingResult(
            @PathVariable UUID jobId,
            @RequestBody @Valid EmbeddingResultRequest request) {
        processingJobService.handleEmbeddingResult(
                jobId,
                request.getFileId(),
                request.getModelName(),
                request.getModelVersion(),
                request.getDimension(),
                request.getEmbedding());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{jobId}/audio-analysis-result")
    public ResponseEntity<Void> reportAudioAnalysisResult(
            @PathVariable UUID jobId,
            @RequestBody @Valid AudioAnalysisResultRequest request) {
        processingJobService.handleAudioAnalysisResult(jobId, request);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{jobId}/failed")
    public ResponseEntity<Void> reportFailure(
            @PathVariable UUID jobId,
            @RequestBody @Valid ProcessingFailureRequest request) {
        processingJobService.handleProcessingFailure(jobId, request.getFileId(), request.getErrorMessage());

        return ResponseEntity.ok().build();
    }
}
