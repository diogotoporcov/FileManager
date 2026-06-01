package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.dto.DuplicateCandidateResponse;
import com.filemanager.api.dto.FileDuplicateResponse;
import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.mapper.FileSummaryResponseMapper;
import com.filemanager.api.port.DuplicateCandidateSearchPort;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateCandidateServiceTest {

    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private DuplicateCandidateSearchPort duplicateCandidateSearchPort;

    @Spy
    private FileSummaryResponseMapper fileSummaryResponseMapper = new FileSummaryResponseMapper();

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
        when(duplicateCandidateSearchPort.search(any(), any())).thenReturn(new PageImpl<>(List.of(candidate)));

        List<FileDuplicateResponse> result = duplicateCandidateService.getDuplicatesForFile(
                file1.getId(), owner.getId(), null, null, null, owner.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRequestedFile().getId()).isEqualTo(file1.getId());
        assertThat(result.getFirst().getDuplicateFile().getId()).isEqualTo(file2.getId());
    }

    @Test
    void getDuplicatesForFile_Bidirectional_Success() {
        when(fileRepository.findByIdAndDeletedAtIsNull(file2.getId())).thenReturn(Optional.of(file2));
        when(duplicateCandidateSearchPort.search(any(), any())).thenReturn(new PageImpl<>(List.of(candidate)));

        List<FileDuplicateResponse> result = duplicateCandidateService.getDuplicatesForFile(
                file2.getId(), owner.getId(), null, null, null, owner.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRequestedFile().getId()).isEqualTo(file2.getId());
        assertThat(result.getFirst().getDuplicateFile().getId()).isEqualTo(file1.getId());
    }

    @Test
    void getDuplicatesForFile_WrongOwner_ThrowsException() {
        when(fileRepository.findByIdAndDeletedAtIsNull(file1.getId())).thenReturn(Optional.of(file1));
        UUID otherOwnerId = UUID.randomUUID();

        assertThatThrownBy(() -> duplicateCandidateService.getDuplicatesForFile(
                file1.getId(), otherOwnerId, null, null, null, otherOwnerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDuplicatesForOwner_Success() {
        when(duplicateCandidateSearchPort.search(any(), any())).thenReturn(new PageImpl<>(List.of(candidate)));

        List<DuplicateCandidateResponse> result = duplicateCandidateService.getDuplicatesForOwner(
                owner.getId(), null, null, null, owner.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getSourceFile().getId()).isEqualTo(file1.getId());
    }

    @Test
    void updateStatus_Success() {
        when(duplicateCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(duplicateCandidateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DuplicateCandidateResponse result = duplicateCandidateService.updateStatus(
                candidate.getId(), owner.getId(), null, CandidateStatus.CONFIRMED, owner.getId());

        assertThat(result.getStatus()).isEqualTo(CandidateStatus.CONFIRMED);
        verify(duplicateCandidateRepository).save(candidate);
        verify(accessControlService).assertCanManageDuplicate(owner.getId(), candidate.getId());
    }

    @Test
    void updateStatus_CrossUserDuplicate_ThrowsException() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        file2.setOwnerUser(otherUser);
        
        when(duplicateCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> duplicateCandidateService.updateStatus(
                candidate.getId(), owner.getId(), null, CandidateStatus.CONFIRMED, owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_Unauthorized_ThrowsException() {
        when(duplicateCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        UUID otherOwnerId = UUID.randomUUID();

        assertThatThrownBy(() -> duplicateCandidateService.updateStatus(
                candidate.getId(), otherOwnerId, null, CandidateStatus.CONFIRMED, otherOwnerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_AccessControlDenied_ThrowsExceptionAndDoesNotSave() {
        doThrow(new com.filemanager.api.exception.AccessDeniedException("Denied"))
                .when(accessControlService).assertCanManageDuplicate(owner.getId(), candidate.getId());

        assertThatThrownBy(() -> duplicateCandidateService.updateStatus(
                candidate.getId(), owner.getId(), null, CandidateStatus.CONFIRMED, owner.getId()))
                .isInstanceOf(com.filemanager.api.exception.AccessDeniedException.class);

        verify(duplicateCandidateRepository, never()).findById(any());
        verify(duplicateCandidateRepository, never()).save(any());
    }

    @Test
    void validateOwnerContext_ThrowsException() {
        assertThatThrownBy(() -> duplicateCandidateService.getDuplicatesForOwner(null, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> duplicateCandidateService.getDuplicatesForOwner(id, id, null, null, id))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
