package com.diogotoporcov.filemanager.api.file.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDownloadRangeTest {

    @Test
    void parsesFirstByteRange() {
        FileDownloadRange range = FileDownloadRange.parse("bytes=0-0", 10L);

        assertEquals(0L, range.getStart());
        assertEquals(0L, range.getEnd());
    }

    @Test
    void parsesOpenEndedRange() {
        FileDownloadRange range = FileDownloadRange.parse("bytes=0-", 10L);

        assertEquals(0L, range.getStart());
        assertEquals(9L, range.getEnd());
    }

    @Test
    void parsesSuffixRange() {
        FileDownloadRange range = FileDownloadRange.parse("bytes=-1", 10L);

        assertEquals(9L, range.getStart());
        assertEquals(9L, range.getEnd());
    }

    @Test
    void clampsEndBeyondFileSize() {
        FileDownloadRange range = FileDownloadRange.parse("bytes=0-999", 10L);

        assertEquals(0L, range.getStart());
        assertEquals(9L, range.getEnd());
    }

    @Test
    void rejectsMultipleRanges() {
        assertThrows(InvalidDownloadRangeException.class, () -> FileDownloadRange.parse("bytes=0-0,2-3", 10L));
    }

    @Test
    void rejectsRangeOnEmptyFile() {
        assertThrows(InvalidDownloadRangeException.class, () -> FileDownloadRange.parse("bytes=0-0", 0L));
        assertThrows(InvalidDownloadRangeException.class, () -> FileDownloadRange.parse("bytes=-1", 0L));
    }
}
