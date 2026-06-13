package com.filemanager.api.duplicate.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRefreshRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRepository;
import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExactDuplicateGroupMaintenanceServiceTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Mock
    private ExactDuplicateGroupRepository exactDuplicateGroupRepository;
    @Mock
    private ExactDuplicateGroupRefreshRepository exactDuplicateGroupRefreshRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ExactDuplicateGroupMaintenanceService service;

    @Test
    void refreshGroupDelegatesToAtomicRefreshOperation() {
        UUID ownerUserId = UUID.randomUUID();

        service.refreshGroup(ownerUserId, FingerprintAlgorithm.SHA256, HASH_A);

        verify(entityManager).flush();
        verify(exactDuplicateGroupRefreshRepository)
                .refreshExactGroup(ownerUserId, FingerprintAlgorithm.SHA256.name(), HASH_A);
        verify(exactDuplicateGroupRepository, never()).save(any());
        verify(exactDuplicateGroupRepository, never())
                .findByOwnerUserIdAndAlgorithmAndHashValue(any(), any(), any());
    }

    @Test
    void refreshGroupIgnoresInvalidInputs() {
        service.refreshGroup(null, FingerprintAlgorithm.SHA256, HASH_A);
        service.refreshGroup(UUID.randomUUID(), null, HASH_A);
        service.refreshGroup(UUID.randomUUID(), FingerprintAlgorithm.SHA256, null);
        service.refreshGroup(UUID.randomUUID(), FingerprintAlgorithm.SHA256, " ");

        verifyNoInteractions(exactDuplicateGroupRefreshRepository);
        verifyNoInteractions(entityManager);
    }

    @Test
    void fingerprintChangeRefreshesOnceWhenHashIsUnchanged() {
        UUID ownerUserId = UUID.randomUUID();

        service.refreshAfterFingerprintChange(ownerUserId, FingerprintAlgorithm.SHA256, HASH_A, HASH_A);

        verify(exactDuplicateGroupRefreshRepository)
                .refreshExactGroup(ownerUserId, FingerprintAlgorithm.SHA256.name(), HASH_A);
    }

    @Test
    void fingerprintChangeRefreshesOldAndNewHashWhenHashChanges() {
        UUID ownerUserId = UUID.randomUUID();

        service.refreshAfterFingerprintChange(ownerUserId, FingerprintAlgorithm.SHA256, HASH_A, HASH_B);

        verify(exactDuplicateGroupRefreshRepository)
                .refreshExactGroup(ownerUserId, FingerprintAlgorithm.SHA256.name(), HASH_A);
        verify(exactDuplicateGroupRefreshRepository)
                .refreshExactGroup(ownerUserId, FingerprintAlgorithm.SHA256.name(), HASH_B);
    }

    @Test
    void rebuildDeletesOwnerGroupsAndRefreshesDistinctHashes() {
        UUID ownerUserId = UUID.randomUUID();
        when(exactDuplicateGroupRepository.findHashesForOwner(ownerUserId, FingerprintAlgorithm.SHA256))
                .thenReturn(List.of(HASH_A, HASH_B));

        service.rebuildOwnerExactGroups(ownerUserId);

        verify(exactDuplicateGroupRepository).deleteByOwnerUserId(ownerUserId);
        verify(exactDuplicateGroupRefreshRepository)
                .refreshExactGroup(ownerUserId, FingerprintAlgorithm.SHA256.name(), HASH_A);
        verify(exactDuplicateGroupRefreshRepository)
                .refreshExactGroup(ownerUserId, FingerprintAlgorithm.SHA256.name(), HASH_B);
    }
}
