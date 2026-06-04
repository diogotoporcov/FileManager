package com.filemanager.api.service;

public interface ProcessingPolicyResolver {
    boolean isEnabled(ProcessingCapability capability, ProcessingPolicyContext context);
}
