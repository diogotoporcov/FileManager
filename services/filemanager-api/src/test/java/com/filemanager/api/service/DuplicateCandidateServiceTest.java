package com.filemanager.api.service;

import com.filemanager.api.dto.*;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicateCandidateServiceTest {

    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private DuplicateCandidateService duplicateCandidateService;

    private User owner;
    private FileEntity file1;
    private FileEntity file2;
    private DuplicateCandidate candidate;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).build();
        file1 = FileEntity.builder()
                .id(UUID.randomUUID())
                .name("file1.jpg")
                .ownerUser(owner)
                .createdAt(OffsetDateTime.now())
                .build();
        file2 = FileEntity.builder()
                .id(UUID.randomUUID())
                .name("file2.jpg")
                .ownerUser(owner)
                .createdAt(OffsetDateTime.now())
                .build();
        candidate = DuplicateCandidate.builder()
                .id(UUID.randomUUID())
                .sourceFile(file1)
                .candidateFile(file2)
                .detectionMethod(DetectionMethod.PHASH)
                .status(CandidateStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void getDuplicatesForFile_Success() {
        when(fileRepository.findByIdAndDeletedAtIsNull(file1.getId())).thenReturn(Optional.of(file1));
        when(duplicateCandidateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(candidate));

        List<FileDuplicateResponse> result = duplicateCandidateService.getDuplicatesForFile(
                file1.getId(), owner.getId(), null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestedFile().getId()).isEqualTo(file1.getId());
        assertThat(result.get(0).getDuplicateFile().getId()).isEqualTo(file2.getId());
    }

    @Test
    void getDuplicatesForFile_Bidirectional_Success() {
        when(fileRepository.findByIdAndDeletedAtIsNull(file2.getId())).thenReturn(Optional.of(file2));
        when(duplicateCandidateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(candidate));

        List<FileDuplicateResponse> result = duplicateCandidateService.getDuplicatesForFile(
                file2.getId(), owner.getId(), null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestedFile().getId()).isEqualTo(file2.getId());
        assertThat(result.get(0).getDuplicateFile().getId()).isEqualTo(file1.getId());
    }

    @Test
    void getDuplicatesForFile_WrongOwner_ThrowsException() {
        when(fileRepository.findByIdAndDeletedAtIsNull(file1.getId())).thenReturn(Optional.of(file1));
        UUID otherOwnerId = UUID.randomUUID();

        assertThatThrownBy(() -> duplicateCandidateService.getDuplicatesForFile(
                file1.getId(), otherOwnerId, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDuplicatesForOwner_Success() {
        when(duplicateCandidateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(candidate));

        List<DuplicateCandidateResponse> result = duplicateCandidateService.getDuplicatesForOwner(
                owner.getId(), null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceFile().getId()).isEqualTo(file1.getId());
    }

    @Test
    void updateStatus_Success() {
        when(duplicateCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(duplicateCandidateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DuplicateCandidateResponse result = duplicateCandidateService.updateStatus(
                candidate.getId(), owner.getId(), null, CandidateStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(CandidateStatus.CONFIRMED);
        verify(duplicateCandidateRepository).save(candidate);
    }

    @Test
    void updateStatus_Unauthorized_ThrowsException() {
        when(duplicateCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        UUID otherOwnerId = UUID.randomUUID();

        assertThatThrownBy(() -> duplicateCandidateService.updateStatus(
                candidate.getId(), otherOwnerId, null, CandidateStatus.CONFIRMED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateOwnerContext_ThrowsException() {
        assertThatThrownBy(() -> duplicateCandidateService.getDuplicatesForOwner(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> duplicateCandidateService.getDuplicatesForOwner(id, id, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
