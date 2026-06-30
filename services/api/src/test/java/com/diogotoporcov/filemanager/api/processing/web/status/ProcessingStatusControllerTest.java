package com.diogotoporcov.filemanager.api.processing.web.status;

import com.diogotoporcov.filemanager.api.application.CursorPage;
import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.processing.application.status.FileProcessingStatus;
import com.diogotoporcov.filemanager.api.processing.application.status.FileProcessingStatusService;
import com.diogotoporcov.filemanager.api.web.CursorPageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
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
                .thenReturn(new CursorPage<>(List.of(), null, false, 0));

        CursorPageResponse<ProcessingJobResponse> result = processingStatusController.getProcessingJobs(fileId, null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void getFileProcessingStatus_ReturnsOk() {
        UUID fileId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(fileProcessingStatusService.getFileProcessingStatus(eq(actorUserId), eq(fileId)))
                .thenReturn(FileProcessingStatus.builder()
                        .fileId(fileId)
                        .overallStatus(FileProcessingStatus.AggregateStatus.NOT_STARTED)
                        .jobs(List.of())
                        .build());

        FileProcessingStatusResponse result = processingStatusController.getFileProcessingStatus(fileId);

        assertThat(result).isNotNull();
    }
}
