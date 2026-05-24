package com.filemanager.api.repository;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import com.filemanager.api.entity.DuplicateCandidate.DetectionMethod;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class DuplicateCandidateSpecifications {

    private DuplicateCandidateSpecifications() { }

    public static Specification<DuplicateCandidate> hasFileId(UUID fileId) {
        return (root, _, cb) -> {
            if (fileId == null) {
                return null;
            }
            return cb.or(
                    cb.equal(root.get("sourceFile").get("id"), fileId),
                    cb.equal(root.get("candidateFile").get("id"), fileId)
            );
        };
    }

    public static Specification<DuplicateCandidate> hasOwnerUserId(UUID userId) {
        return (root, _, cb) -> {
            if (userId == null) {
                return null;
            }
            return cb.and(
                    cb.equal(root.get("sourceFile").get("ownerUser").get("id"), userId),
                    cb.equal(root.get("candidateFile").get("ownerUser").get("id"), userId)
            );
        };
    }

    public static Specification<DuplicateCandidate> hasOwnerOrganizationId(UUID orgId) {
        return (root, _, cb) -> {
            if (orgId == null) {
                return null;
            }
            return cb.and(
                    cb.equal(root.get("sourceFile").get("ownerOrganization").get("id"), orgId),
                    cb.equal(root.get("candidateFile").get("ownerOrganization").get("id"), orgId)
            );
        };
    }

    public static Specification<DuplicateCandidate> isNotDeleted() {
        return (root, _, cb) -> cb.and(
                cb.isNull(root.get("sourceFile").get("deletedAt")),
                cb.isNull(root.get("candidateFile").get("deletedAt"))
        );
    }

    public static Specification<DuplicateCandidate> hasDetectionMethod(DetectionMethod method) {
        return (root, _, cb) -> method == null ? null : cb.equal(root.get("detectionMethod"), method);
    }

    public static Specification<DuplicateCandidate> hasStatus(CandidateStatus status) {
        return (root, _, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
