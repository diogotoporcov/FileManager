package com.filemanager.api.search;

public class SearchValidationException extends IllegalArgumentException {
    public SearchValidationException(String message) {
        super(message);
    }

    public SearchValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
