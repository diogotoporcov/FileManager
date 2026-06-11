package com.filemanager.api.duplicate.domain;

public enum DuplicateMethodStatus {
    COMPLETED,
    UNSUPPORTED_FOR_FILE_TYPE,
    SOURCE_FINGERPRINT_NOT_READY,
    DISABLED_BY_CONFIG,
    NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET
}