package com.filemanager.api.adapter;

import com.filemanager.api.entity.DuplicateCandidate;
import com.filemanager.api.port.DuplicateCandidateSearchPort;
import com.filemanager.api.port.DuplicateCandidateSearchRequest;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.DuplicateCandidateSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaDuplicateCandidateSearchAdapter implements DuplicateCandidateSearchPort {

    private final DuplicateCandidateRepository duplicateCandidateRepository;

    @Override
    public Page<DuplicateCandidate> search(DuplicateCandidateSearchRequest request, Pageable pageable) {
        Specification<DuplicateCandidate> spec = Specification.where(DuplicateCandidateSpecifications.isNotDeleted())
                .and(DuplicateCandidateSpecifications.hasDetectionMethod(request.method()))
                .and(DuplicateCandidateSpecifications.hasStatus(request.status()));

        if (request.fileId() != null) {
            spec = spec.and(DuplicateCandidateSpecifications.hasFileId(request.fileId()));
        }

        if (request.ownerUserId() != null) {
            spec = spec.and(DuplicateCandidateSpecifications.hasOwnerUserId(request.ownerUserId()));
        } else {
            spec = spec.and(DuplicateCandidateSpecifications.hasOwnerOrganizationId(request.ownerOrganizationId()));
        }

        return duplicateCandidateRepository.findAll(spec, pageable);
    }
}
