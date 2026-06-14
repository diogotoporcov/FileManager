package com.filemanager.api.duplicate.application;

import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRefreshRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRepository;
import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExactDuplicateGroupMaintenanceService {
    private final ExactDuplicateGroupRepository exactDuplicateGroupRepository;
    private final ExactDuplicateGroupRefreshRepository exactDuplicateGroupRefreshRepository;
    private final EntityManager entityManager;

    @Transactional
    public void refreshGroup(UUID ownerUserId, FingerprintAlgorithm algorithm, String hashValue) {
        if (ownerUserId == null || algorithm == null || hashValue == null || hashValue.isBlank()) {
            return;
        }

        entityManager.flush();
        exactDuplicateGroupRefreshRepository.refreshExactGroup(ownerUserId, algorithm.name(), hashValue);
    }

    @Transactional
    public void refreshAfterFingerprintChange(
            UUID ownerUserId,
            FingerprintAlgorithm algorithm,
            String oldHashValue,
            String newHashValue) {
        if (Objects.equals(oldHashValue, newHashValue)) {
            refreshGroup(ownerUserId, algorithm, newHashValue);
            return;
        }

        refreshGroup(ownerUserId, algorithm, oldHashValue);
        refreshGroup(ownerUserId, algorithm, newHashValue);
    }

    @Transactional
    public void rebuildOwnerExactGroups(UUID ownerUserId) {
        List<String> hashes = exactDuplicateGroupRepository.findHashesForOwner(ownerUserId, FingerprintAlgorithm.SHA256);
        exactDuplicateGroupRepository.deleteByOwnerUserId(ownerUserId);

        for (String hash : hashes) {
            refreshGroup(ownerUserId, FingerprintAlgorithm.SHA256, hash);
        }
    }
}
