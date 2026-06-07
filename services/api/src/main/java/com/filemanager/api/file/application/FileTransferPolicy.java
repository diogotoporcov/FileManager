package com.filemanager.api.file.application;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

public final class FileTransferPolicy {
    public static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    public static final String DEFAULT_DOWNLOAD_FILENAME = "download";
    private static final int MAX_FILENAME_LENGTH = 255;

    private FileTransferPolicy() {
    }

    public static String normalizeUploadFilename(String filename) {
        String normalized = basename(filename);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Filename is missing");
        }

        if (".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("Filename is invalid");
        }

        if (normalized.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Filename must not exceed " + MAX_FILENAME_LENGTH + " characters");
        }

        if (containsControlCharacter(normalized)) {
            throw new IllegalArgumentException("Filename must not contain control characters");
        }

        return normalized;
    }

    public static String safeDownloadFilename(String filename) {
        String normalized = basename(filename);
        if (normalized == null || normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            return DEFAULT_DOWNLOAD_FILENAME;
        }

        StringBuilder safe = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            safe.append(Character.isISOControl(current) ? '_' : current);
        }

        String value = safe.toString();
        if (value.isBlank()) {
            return DEFAULT_DOWNLOAD_FILENAME;
        }

        if (value.length() > MAX_FILENAME_LENGTH) {
            return value.substring(0, MAX_FILENAME_LENGTH);
        }

        return value;
    }

    public static String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }

        try {
            return MediaType.parseMediaType(contentType).toString();
        } catch (IllegalArgumentException ex) {
            return DEFAULT_CONTENT_TYPE;
        }
    }

    public static String attachmentContentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(safeDownloadFilename(filename), StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    public static String safeEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            return null;
        }

        String trimmed = etag.trim();
        if (containsControlCharacter(trimmed) || trimmed.contains("\"") || trimmed.contains("\\")) {
            return null;
        }

        return "\"" + trimmed + "\"";
    }

    private static String basename(String filename) {
        if (filename == null) {
            return null;
        }

        String trimmed = filename.trim().replace('\\', '/');
        int separator = trimmed.lastIndexOf('/');
        if (separator >= 0) {
            return trimmed.substring(separator + 1).trim();
        }

        return trimmed;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }

        return false;
    }
}
