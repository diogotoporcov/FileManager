package com.filemanager.api.file.application.search;

import com.filemanager.api.file.domain.FileEntity;
import org.springframework.data.domain.Sort;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public record FileSearchCursor(String sortField, Sort.Direction direction, String value, UUID id) {
    private static final String VERSION = "v2";

    public FileSearchCursor {
        if (sortField == null || sortField.isBlank()) {
            throw new SearchValidationException("Cursor sort field is required");
        }

        if (direction == null) {
            throw new SearchValidationException("Cursor sort direction is required");
        }

        if (value == null || value.isBlank()) {
            throw new SearchValidationException("Cursor value is required");
        }

        if (id == null) {
            throw new SearchValidationException("Cursor id is required");
        }
    }

    public static FileSearchCursor decode(String rawCursor, SortSpec sort) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }

        String decoded = decodeBase64(rawCursor);
        if (decoded.startsWith(VERSION + "|")) {
            return decodeCurrent(decoded, sort);
        }

        if (!"createdAt".equals(sort.field()) || sort.direction() != Sort.Direction.DESC) {
            throw new SearchValidationException("Cursor does not match requested sort");
        }

        String[] parts = decoded.split("\\|", 2);
        if (parts.length != 2) {
            throw new SearchValidationException("Invalid cursor");
        }

        try {
            OffsetDateTime.parse(parts[0]);

            return new FileSearchCursor("createdAt", Sort.Direction.DESC, parts[0], UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new SearchValidationException("Invalid cursor", ex);
        }
    }

    public static String encode(SortSpec sort, FileEntity entity) {
        if (entity == null) {
            return null;
        }

        String value = switch (sort.field()) {
            case "createdAt" -> entity.getCreatedAt().toString();
            case "updatedAt" -> entity.getUpdatedAt().toString();
            case "name" -> entity.getName();
            case "size" -> entity.getSize().toString();
            default -> throw new SearchValidationException("Unsupported sort field: " + sort.field());
        };

        String raw = String.join("|",
                VERSION,
                sort.field(),
                sort.direction().name().toLowerCase(),
                encodeBase64(value),
                entity.getId().toString());

        return encodeBase64(raw);
    }

    private static FileSearchCursor decodeCurrent(String decoded, SortSpec sort) {
        String[] parts = decoded.split("\\|", 5);
        if (parts.length != 5) {
            throw new SearchValidationException("Invalid cursor");
        }

        Sort.Direction cursorDirection = FileSortDirections.parse(parts[2]);
        if (!parts[1].equals(sort.field()) || cursorDirection != sort.direction()) {
            throw new SearchValidationException("Cursor does not match requested sort");
        }

        try {
            return new FileSearchCursor(parts[1], cursorDirection, decodeBase64(parts[3]), UUID.fromString(parts[4]));
        } catch (RuntimeException ex) {
            throw new SearchValidationException("Invalid cursor", ex);
        }
    }

    private static String encodeBase64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            throw new SearchValidationException("Invalid cursor", ex);
        }
    }
}
