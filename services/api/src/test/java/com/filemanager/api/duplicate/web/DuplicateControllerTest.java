package com.filemanager.api.duplicate.web;

import com.filemanager.api.auth.application.CurrentUserService;
import com.filemanager.api.duplicate.application.DuplicateSearchService;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateControllerTest {
    @Mock
    private DuplicateSearchService duplicateSearchService;
    @Mock
    private CurrentUserService currentUserService;

    private DuplicateController controller;

    @BeforeEach
    void setUp() {
        controller = new DuplicateController(duplicateSearchService, currentUserService);
    }

    @Test
    void omittedMethodsPassesEmptySelectionForServiceDefault() {
        UUID actorUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(duplicateSearchService.searchDuplicatesForFile(eq(fileId), any(), eq(actorUserId)))
                .thenReturn(new DuplicateSearchResponse(fileId, List.of()));

        controller.findDuplicatesForFile(fileId, null);

        verify(duplicateSearchService).searchDuplicatesForFile(fileId, List.of(), actorUserId);
    }

    @Test
    void commaSeparatedMethodsAreParsedCaseInsensitively() {
        UUID actorUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(duplicateSearchService.searchDuplicatesForFile(eq(fileId), any(), eq(actorUserId)))
                .thenReturn(new DuplicateSearchResponse(fileId, List.of()));

        controller.findDuplicatesForFile(fileId, "exact,image_phash,video_embedding");

        verify(duplicateSearchService).searchDuplicatesForFile(
                fileId,
                List.of(
                        DuplicateSearchMethod.EXACT,
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateSearchMethod.VIDEO_EMBEDDING),
                actorUserId);
    }

    @Test
    void invalidMethodReturnsInvalidRequestException() {
        UUID fileId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.findDuplicatesForFile(fileId, "EXACT,NOPE"));

        assertThat(exception.getMessage()).contains("Invalid duplicate search method");
    }

    @Test
    void removedVideoMethodNamesAreRejected() {
        UUID fileId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        for (String removedMethod : List.of(
                "VIDEO_FRAME_PHASH",
                "VIDEO_FRAME_EMBEDDING",
                "VIDEO_AV_FINGERPRINT",
                "VIDEO_AUDIO_FINGERPRINT")) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.findDuplicatesForFile(fileId, removedMethod));
            assertThat(exception.getMessage()).contains("Invalid duplicate search method");
        }
    }
}
