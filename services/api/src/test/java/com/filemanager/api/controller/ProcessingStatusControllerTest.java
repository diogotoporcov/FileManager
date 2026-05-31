package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.CursorPageResponse;
import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.service.FileProcessingStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingStatusControllerTest {

    @Mock
    private FileProcessingStatusService fileProcessingStatusService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ProcessingStatusController processingStatusController;

    @Test
    void getProcessingJobs_ReturnsOk() {
        UUID fileId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(fileProcessingStatusService.getProcessingJobs(eq(actorUserId), eq(fileId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(CursorPageResponse.<ProcessingJobResponse>builder().items(java.util.List.of()).build());

        CursorPageResponse<ProcessingJobResponse> result = processingStatusController.getProcessingJobs(fileId, null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void getFileProcessingStatus_ReturnsOk() {
        UUID fileId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(fileProcessingStatusService.getFileProcessingStatus(eq(actorUserId), eq(fileId)))
                .thenReturn(FileProcessingStatusResponse.builder().build());

        FileProcessingStatusResponse result = processingStatusController.getFileProcessingStatus(fileId);

        assertThat(result).isNotNull();
    }
}
