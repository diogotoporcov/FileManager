package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.DuplicateCandidateResponse;
import com.filemanager.api.dto.DuplicateStatusUpdateRequest;
import com.filemanager.api.dto.FileDuplicateResponse;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.service.DuplicateCandidateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateCandidateControllerTest {

    @Mock
    private DuplicateCandidateService duplicateCandidateService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private DuplicateCandidateController duplicateCandidateController;

    @Test
    void getDuplicatesForFile_ReturnsOk() {
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(ownerId);
        when(duplicateCandidateService.getDuplicatesForFile(eq(fileId), eq(ownerId), eq(null), any(), any(), eq(ownerId)))
                .thenReturn(List.of());

        List<FileDuplicateResponse> result = duplicateCandidateController.getDuplicatesForFile(
                fileId, ownerId, null, null, null);
        
        assertThat(result).isNotNull();
    }

    @Test
    void getDuplicatesForOwner_ReturnsOk() {
        UUID ownerId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(ownerId);
        when(duplicateCandidateService.getDuplicatesForOwner(eq(ownerId), eq(null), any(), any(), eq(ownerId)))
                .thenReturn(List.of());

        List<DuplicateCandidateResponse> result = duplicateCandidateController.getDuplicatesForOwner(
                ownerId, null, null, null);
        
        assertThat(result).isNotNull();
    }

    @Test
    void updateStatus_ReturnsOk() {
        UUID candidateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DuplicateStatusUpdateRequest request = new DuplicateStatusUpdateRequest(CandidateStatus.CONFIRMED);

        when(currentUserService.getCurrentUserId()).thenReturn(ownerId);
        when(duplicateCandidateService.updateStatus(eq(candidateId), eq(ownerId), eq(null), eq(CandidateStatus.CONFIRMED), eq(ownerId)))
                .thenReturn(DuplicateCandidateResponse.builder().build());

        DuplicateCandidateResponse result = duplicateCandidateController.updateStatus(
                candidateId, ownerId, null, request);
        
        assertThat(result).isNotNull();
    }
}
