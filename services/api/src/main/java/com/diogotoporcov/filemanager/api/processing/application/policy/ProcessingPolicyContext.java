package com.diogotoporcov.filemanager.api.processing.application.policy;

import com.diogotoporcov.filemanager.api.processing.domain.ProcessingJob;

import java.util.UUID;

public record ProcessingPolicyContext(
        UUID ownerUserId,
        UUID folderId,
        String mimeType,
        ProcessingJob.JobType jobType) {

    public static ProcessingPolicyContext forMimeType(String mimeType) {
        return new ProcessingPolicyContext(null, null, mimeType, null);
    }

    public ProcessingPolicyContext withJobType(ProcessingJob.JobType jobType) {
        return new ProcessingPolicyContext(ownerUserId, folderId, mimeType, jobType);
    }
}
