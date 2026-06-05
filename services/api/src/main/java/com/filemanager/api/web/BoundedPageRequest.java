package com.filemanager.api.web;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public record BoundedPageRequest(int size, String cursor) {
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    public static BoundedPageRequest of(Integer requestedSize, String cursor) {
        int effectiveSize = requestedSize == null ? DEFAULT_SIZE : requestedSize;

        if (effectiveSize < 1) {
            throw new IllegalArgumentException("Page size must be positive");
        }

        if (effectiveSize > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must not exceed " + MAX_SIZE);
        }

        return new BoundedPageRequest(effectiveSize, cursor);
    }

    public int fetchSize() {
        return size + 1;
    }

    public SeekCursor decodedCursor() {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        String decoded = decodeBase64(cursor);
        String[] parts = decoded.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor");
        }

        try {
            return new SeekCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid cursor", ex);
        }
    }

    public static String encodeCursor(OffsetDateTime createdAt, UUID id) {
        if (createdAt == null || id == null) {
            return null;
        }

        String raw = createdAt + "|" + id;

        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid cursor", ex);
        }
    }

    public record SeekCursor(OffsetDateTime createdAt, UUID id) {
    }
}
