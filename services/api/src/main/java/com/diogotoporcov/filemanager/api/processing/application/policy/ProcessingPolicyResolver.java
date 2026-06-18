package com.diogotoporcov.filemanager.api.processing.application.policy;

public interface ProcessingPolicyResolver {
    boolean isEnabled(ProcessingCapability capability, ProcessingPolicyContext context);
}
