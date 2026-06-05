package com.filemanager.api.search.file;

import com.filemanager.api.dto.BoundedPageRequest;
import com.filemanager.api.search.SearchValidationException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Component
public class FileSearchCriteriaMapper {
    private static final int MAX_MIME_TYPES = 20;

    private final FileSortMapper fileSortMapper;

    public FileSearchCriteriaMapper(FileSortMapper fileSortMapper) {
        this.fileSortMapper = fileSortMapper;
    }

    public FileSearchCriteria toCriteria(FileSearchQuery query, UUID actorUserId) {
        SortSpec sort = fileSortMapper.parse(query.getSort());
        BoundedPageRequest pageRequest = BoundedPageRequest.of(requestedLimit(query), query.getCursor());
        FileSearchCursor cursor = FileSearchCursor.decode(query.getCursor(), sort);

        return new FileSearchCriteria(
                actorUserId,
                query.getFolderId(),
                query.getTagId(),
                new DateTimeRange(parseDateTime("createdAtFrom", query.getCreatedAtFrom()), parseDateTime("createdAtTo", query.getCreatedAtTo())),
                new DateTimeRange(parseDateTime("updatedAtFrom", query.getUpdatedAtFrom()), parseDateTime("updatedAtTo", query.getUpdatedAtTo())),
                new LongRange(query.getSizeMin(), query.getSizeMax()),
                normalizeMimeTypes(query.getMimeType()),
                sort,
                pageRequest,
                cursor);
    }

    private Integer requestedLimit(FileSearchQuery query) {
        if (query.getSize() != null && query.getLimit() != null && !query.getSize().equals(query.getLimit())) {
            throw new SearchValidationException("Specify either size or limit, not both");
        }

        return query.getLimit() != null ? query.getLimit() : query.getSize();
    }

    private OffsetDateTime parseDateTime(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new SearchValidationException("Invalid date format for " + field + ". Use ISO-8601 offset date-time.", ex);
        }
    }

    private List<String> normalizeMimeTypes(List<String> rawMimeTypes) {
        if (rawMimeTypes == null || rawMimeTypes.isEmpty()) {
            return List.of();
        }

        if (rawMimeTypes.size() > MAX_MIME_TYPES) {
            throw new SearchValidationException("Too many MIME type filters");
        }

        return rawMimeTypes.stream()
                .map(value -> value == null ? "" : value.trim())
                .peek(value -> {
                    if (value.isBlank()) {
                        throw new SearchValidationException("MIME type filter must not be blank");
                    }
                })
                .distinct()
                .toList();
    }
}