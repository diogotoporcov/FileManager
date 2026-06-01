package com.filemanager.api.search.file;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.search.SearchValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSearchCursorTest {

    @Test
    void encodeAndDecodeCurrentCursorPreservesRequestedSortAndTieBreakerId() {
        UUID fileId = UUID.randomUUID();
        FileEntity file = new FileEntity();
        file.setId(fileId);
        file.setName("Quarterly report");
        file.setSize(42L);
        file.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        file.setUpdatedAt(OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        SortSpec sort = new SortSpec("name", Sort.Direction.ASC);

        String encoded = FileSearchCursor.encode(sort, file);
        FileSearchCursor decoded = FileSearchCursor.decode(encoded, sort);

        assertEquals("name", decoded.sortField());
        assertEquals(Sort.Direction.ASC, decoded.direction());
        assertEquals("Quarterly report", decoded.value());
        assertEquals(fileId, decoded.id());
    }

    @Test
    void decodeRejectsCursorCreatedForDifferentSort() {
        FileEntity file = new FileEntity();
        file.setId(UUID.randomUUID());
        file.setName("Alpha");
        file.setSize(10L);
        file.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        file.setUpdatedAt(OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        String encoded = FileSearchCursor.encode(new SortSpec("name", Sort.Direction.ASC), file);

        assertThrows(SearchValidationException.class,
                () -> FileSearchCursor.decode(encoded, new SortSpec("createdAt", Sort.Direction.DESC)));
    }

    @Test
    void decodeSupportsLegacyCreatedAtDescendingCursor() {
        UUID fileId = UUID.randomUUID();
        String createdAt = "2026-01-01T00:00:00Z";
        String encoded = encodeBase64(createdAt + "|" + fileId);

        FileSearchCursor decoded = FileSearchCursor.decode(
                encoded,
                new SortSpec("createdAt", Sort.Direction.DESC));

        assertEquals("createdAt", decoded.sortField());
        assertEquals(Sort.Direction.DESC, decoded.direction());
        assertEquals(createdAt, decoded.value());
        assertEquals(fileId, decoded.id());
    }

    @Test
    void decodeRejectsLegacyCursorWhenRequestedSortChanged() {
        String encoded = encodeBase64("2026-01-01T00:00:00Z|" + UUID.randomUUID());

        assertThrows(SearchValidationException.class,
                () -> FileSearchCursor.decode(encoded, new SortSpec("name", Sort.Direction.ASC)));
    }

    @Test
    void decodeRejectsMalformedBase64() {
        assertThrows(SearchValidationException.class,
                () -> FileSearchCursor.decode("not valid base64", new SortSpec("createdAt", Sort.Direction.DESC)));
    }

    @Test
    void decodeBlankCursorReturnsNull() {
        assertNull(FileSearchCursor.decode(" ", new SortSpec("createdAt", Sort.Direction.DESC)));
    }

    private String encodeBase64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
