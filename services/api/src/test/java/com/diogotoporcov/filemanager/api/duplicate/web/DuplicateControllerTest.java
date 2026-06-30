package com.diogotoporcov.filemanager.api.duplicate.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateSearchResult;
import com.diogotoporcov.filemanager.api.duplicate.application.DuplicateSearchService;
import com.diogotoporcov.filemanager.api.duplicate.domain.DuplicateSearchMethod;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
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
        when(duplicateSearchService.searchDuplicatesForFile(eq(fileId), any(), eq(actorUserId), any()))
                .thenReturn(new DuplicateSearchResult(fileId, List.of()));

        controller.findDuplicatesForFile(fileId, null, null, null);

        verify(duplicateSearchService).searchDuplicatesForFile(
                eq(fileId),
                eq(List.of()),
                eq(actorUserId),
                argThat(request -> request.pageSize() == null && request.cursor() == null));
    }

    @Test
    void commaSeparatedMethodsAreParsedCaseInsensitively() {
        UUID actorUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(duplicateSearchService.searchDuplicatesForFile(eq(fileId), any(), eq(actorUserId), any()))
                .thenReturn(new DuplicateSearchResult(fileId, List.of()));

        controller.findDuplicatesForFile(fileId, "exact,image_phash,audio_fingerprint", 25, null);

        verify(duplicateSearchService).searchDuplicatesForFile(
                eq(fileId),
                eq(List.of(
                        DuplicateSearchMethod.EXACT,
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateSearchMethod.AUDIO_FINGERPRINT)),
                eq(actorUserId),
                argThat(request -> request.pageSize().equals(25) && request.cursor() == null));
    }

    @Test
    void singleMethodCursorIsForwarded() {
        UUID actorUserId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(actorUserId);
        when(duplicateSearchService.searchDuplicatesForFile(eq(fileId), any(), eq(actorUserId), any()))
                .thenReturn(new DuplicateSearchResult(fileId, List.of()));

        controller.findDuplicatesForFile(fileId, "exact", 25, "cursor");

        verify(duplicateSearchService).searchDuplicatesForFile(
                eq(fileId),
                eq(List.of(DuplicateSearchMethod.EXACT)),
                eq(actorUserId),
                argThat(request -> request.pageSize().equals(25) && request.cursor().equals("cursor")));
    }

    @Test
    void invalidMethodReturnsInvalidRequestException() {
        UUID fileId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.findDuplicatesForFile(fileId, "EXACT,NOPE", null, null));

        assertThat(exception.getMessage()).contains("Invalid duplicate search method");
        verify(currentUserService, never()).getCurrentUserId();
    }

    @Test
    void cursorWithMultipleMethodsIsRejectedBeforeServiceCall() {
        UUID fileId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.findDuplicatesForFile(fileId, "EXACT,IMAGE_PHASH", null, "cursor"));

        assertThat(exception.getMessage()).contains("exactly one method");
        verify(currentUserService, never()).getCurrentUserId();
        verify(duplicateSearchService, never()).searchDuplicatesForFile(any(), any(), any(), any());
    }

    @Test
    void cursorWithoutExplicitMethodIsRejectedBeforeServiceCall() {
        UUID fileId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.findDuplicatesForFile(fileId, null, null, "cursor"));

        assertThat(exception.getMessage()).contains("exactly one method");
        verify(currentUserService, never()).getCurrentUserId();
        verify(duplicateSearchService, never()).searchDuplicatesForFile(any(), any(), any(), any());
    }

    @Test
    void removedVideoMethodNamesAreRejected() {
        UUID fileId = UUID.randomUUID();

        for (String removedMethod : List.of(
                "VIDEO_FRAME_PHASH",
                "VIDEO_FRAME_EMBEDDING",
                "VIDEO_EMBEDDING",
                "VIDEO_AV_FINGERPRINT",
                "VIDEO_AUDIO_FINGERPRINT")) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.findDuplicatesForFile(fileId, removedMethod, null, null));
            assertThat(exception.getMessage()).contains("Invalid duplicate search method");
        }
    }
}
