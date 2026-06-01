package com.filemanager.api.search.file;

import com.filemanager.api.search.SearchValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSortMapperTest {
    private final FileSortMapper mapper = new FileSortMapper();

    @Test
    void deterministicIdTieBreakerApplied() {
        var sort = mapper.toSort(mapper.parse("createdAt,desc"));

        assertEquals("createdAt: DESC,id: DESC", sort.toString());
    }

    @Test
    void sizeSortIncludesIdTieBreaker() {
        var sort = mapper.toSort(mapper.parse("size,asc"));

        assertEquals("size: ASC,id: ASC", sort.toString());
    }

    @Test
    void unsupportedSortFieldRejected() {
        assertThrows(SearchValidationException.class, () -> mapper.parse("deletedAt,desc"));
    }
}
