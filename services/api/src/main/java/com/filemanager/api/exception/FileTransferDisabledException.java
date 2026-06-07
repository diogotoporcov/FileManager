package com.filemanager.api.exception;

public class FileTransferDisabledException extends RuntimeException {
    public FileTransferDisabledException(String message) {
        super(message);
    }
}
