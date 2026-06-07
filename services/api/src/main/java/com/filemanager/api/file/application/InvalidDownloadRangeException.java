package com.filemanager.api.file.application;

import lombok.Getter;

@Getter
public class InvalidDownloadRangeException extends RuntimeException {
    private final long completeSize;

    public InvalidDownloadRangeException(long completeSize) {
        super("Requested byte range is not satisfiable");
        this.completeSize = completeSize;
    }
}
