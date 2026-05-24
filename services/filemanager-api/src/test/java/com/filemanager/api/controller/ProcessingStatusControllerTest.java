package com.filemanager.api.controller;

import com.filemanager.api.dto.FileProcessingStatusResponse;
import com.filemanager.api.dto.ProcessingJobResponse;
import com.filemanager.api.service.FileProcessingStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingStatusControllerTest {

    @Mock
    private FileProcessingStatusService fileProcessingStatusService;

    @InjectMocks
    private ProcessingStatusController processingStatusController;

    @Test
    void getProcessingJobs_ReturnsOk() {
        UUID fileId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        when(fileProcessingStatusService.getProcessingJobs(eq(actorUserId), eq(fileId)))
                .thenReturn(List.of());

        List<ProcessingJobResponse> result = processingStatusController.getProcessingJobs(fileId, actorUserId);

        assertThat(result).isNotNull();
    }

    @Test
    void getFileProcessingStatus_ReturnsOk() {
        UUID fileId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        when(fileProcessingStatusService.getFileProcessingStatus(eq(actorUserId), eq(fileId)))
                .thenReturn(FileProcessingStatusResponse.builder().build());

        FileProcessingStatusResponse result = processingStatusController.getFileProcessingStatus(fileId, actorUserId);

        assertThat(result).isNotNull();
    }
}
