package com.filemanager.api.controller;

import com.filemanager.api.dto.internal.ChecksumResultRequest;
import com.filemanager.api.dto.internal.PhashResultRequest;
import com.filemanager.api.dto.internal.ProcessingFailureRequest;
import com.filemanager.api.service.ProcessingJobService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PostMapping("/{jobId}/failed")
    public ResponseEntity<Void> reportFailure(
            @PathVariable UUID jobId,
            @RequestBody @Valid ProcessingFailureRequest request) {
        processingJobService.handleProcessingFailure(jobId, request.getFileId(), request.getErrorMessage());
        return ResponseEntity.ok().build();
    }
}
