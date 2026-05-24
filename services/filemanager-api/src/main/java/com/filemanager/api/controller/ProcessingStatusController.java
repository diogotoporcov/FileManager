package com.filemanager.api.controller;

import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.service.FileProcessingStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class ProcessingStatusController {

    private final FileProcessingStatusService fileProcessingStatusService;

    @GetMapping("/{fileId}/processing-jobs")
    public List<ProcessingJobResponse> getProcessingJobs(
            @PathVariable UUID fileId,
            @RequestParam UUID actorUserId) {
        return fileProcessingStatusService.getProcessingJobs(actorUserId, fileId);
    }

    @GetMapping("/{fileId}/processing-status")
    public FileProcessingStatusResponse getFileProcessingStatus(
            @PathVariable UUID fileId,
            @RequestParam UUID actorUserId) {
        return fileProcessingStatusService.getFileProcessingStatus(actorUserId, fileId);
    }
}
