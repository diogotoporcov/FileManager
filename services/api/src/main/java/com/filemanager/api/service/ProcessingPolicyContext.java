package com.filemanager.api.service;

import com.filemanager.api.entity.ProcessingJob;

import java.util.UUID;

public record ProcessingPolicyContext(
        UUID ownerUserId,
        UUID ownerOrganizationId,
        UUID folderId,
        String mimeType,
        ProcessingJob.JobType jobType) {

    public static ProcessingPolicyContext forMimeType(String mimeType) {
        return new ProcessingPolicyContext(null, null, null, mimeType, null);
    }

    public ProcessingPolicyContext withJobType(ProcessingJob.JobType jobType) {
        return new ProcessingPolicyContext(ownerUserId, ownerOrganizationId, folderId, mimeType, jobType);
    }
}
