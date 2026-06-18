package com.diogotoporcov.filemanager.api.file.application.search;

import com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery;
import com.diogotoporcov.filemanager.api.web.BoundedPageRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSearchCriteriaMapperTest {
    private final FileSearchCriteriaMapper mapper = new FileSearchCriteriaMapper(new FileSortMapper());
    private final UUID actorUserId = UUID.randomUUID();

    @Test
    void validDateRangeAccepted() {
        FileSearchQuery query = baseQuery();
        query.setCreatedAtFrom("2026-01-01T00:00:00Z");
        query.setCreatedAtTo("2026-02-01T00:00:00Z");

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals("2026-01-01T00:00Z", criteria.createdAt().from().toString());
        assertEquals("2026-02-01T00:00Z", criteria.createdAt().to().toString());
    }

    @Test
    void invalidDateRangeRejected() {
        FileSearchQuery query = baseQuery();
        query.setCreatedAtFrom("2026-02-01T00:00:00Z");
        query.setCreatedAtTo("2026-01-01T00:00:00Z");

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void invalidDateFormatRejected() {
        FileSearchQuery query = baseQuery();
        query.setUpdatedAtFrom("not-a-date");

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void validSizeRangeAccepted() {
        FileSearchQuery query = baseQuery();
        query.setSizeMin(1L);
        query.setSizeMax(10L);

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals(1L, criteria.size().min());
        assertEquals(10L, criteria.size().max());
    }

    @Test
    void negativeSizeRejected() {
        FileSearchQuery query = baseQuery();
        query.setSizeMin(-1L);

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void sizeMinGreaterThanSizeMaxRejected() {
        FileSearchQuery query = baseQuery();
        query.setSizeMin(10L);
        query.setSizeMax(1L);

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void validSortAccepted() {
        FileSearchQuery query = baseQuery();
        query.setSort("size,asc");

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals("size", criteria.sort().field());
        assertEquals(Sort.Direction.ASC, criteria.sort().direction());
    }

    @Test
    void unsupportedSortFieldRejected() {
        FileSearchQuery query = baseQuery();
        query.setSort("storagePath,desc");

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void invalidSortDirectionRejected() {
        FileSearchQuery query = baseQuery();
        query.setSort("createdAt,newest");

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void repeatedMimeTypeListAccepted() {
        FileSearchQuery query = baseQuery();
        query.setMimeType(List.of("image/jpeg", "image/png"));

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals(List.of("image/jpeg", "image/png"), criteria.mimeTypes());
    }

    @Test
    void blankMimeTypeRejected() {
        FileSearchQuery query = baseQuery();
        query.setMimeType(List.of("image/jpeg", " "));

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void tooManyMimeTypesRejected() {
        FileSearchQuery query = baseQuery();
        List<String> mimeTypes = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            mimeTypes.add("application/test-" + i);
        }
        query.setMimeType(mimeTypes);

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void pageSizeAboveMaxRejected() {
        FileSearchQuery query = baseQuery();
        query.setLimit(BoundedPageRequest.MAX_SIZE + 1);

        assertThrows(IllegalArgumentException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void zeroLimitRejected() {
        FileSearchQuery query = baseQuery();
        query.setLimit(0);

        assertThrows(IllegalArgumentException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void sameSizeAndLimitAccepted() {
        FileSearchQuery query = baseQuery();
        query.setSize(25);
        query.setLimit(25);

        assertEquals(25, mapper.toCriteria(query, actorUserId).pageRequest().size());
    }

    @Test
    void differentSizeAndLimitRejected() {
        FileSearchQuery query = baseQuery();
        query.setSize(25);
        query.setLimit(50);

        assertThrows(SearchValidationException.class, () -> mapper.toCriteria(query, actorUserId));
    }

    @Test
    void defaultSortIsCreatedAtDescending() {
        FileSearchQuery query = baseQuery();

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals("createdAt", criteria.sort().field());
        assertEquals(Sort.Direction.DESC, criteria.sort().direction());
        assertTrue(criteria.mimeTypes().isEmpty());
    }

    @Test
    void folderIdIsMappedToCriteria() {
        UUID folderId = UUID.randomUUID();
        FileSearchQuery query = baseQuery();
        query.setFolderId(folderId);

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals(folderId, criteria.folderId());
    }

    @Test
    void tagIdIsMappedToCriteria() {
        UUID tagId = UUID.randomUUID();
        FileSearchQuery query = baseQuery();
        query.setTagId(tagId);

        var criteria = mapper.toCriteria(query, actorUserId);

        assertEquals(tagId, criteria.tagId());
    }

    private FileSearchQuery baseQuery() {
        return new FileSearchQuery();
    }
}
