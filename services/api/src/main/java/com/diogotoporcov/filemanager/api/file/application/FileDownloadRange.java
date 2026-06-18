package com.diogotoporcov.filemanager.api.file.application;

import lombok.Value;

@Value
public class FileDownloadRange {
    Long start;
    Long end;

    public static FileDownloadRange parse(String rangeHeader, long completeSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }

        if (!rangeHeader.startsWith("bytes=") || rangeHeader.indexOf(',') >= 0) {
            throw new InvalidDownloadRangeException(completeSize);
        }

        String rangeValue = rangeHeader.substring("bytes=".length()).trim();
        int separator = rangeValue.indexOf('-');
        if (separator < 0) {
            throw new InvalidDownloadRangeException(completeSize);
        }

        String startValue = rangeValue.substring(0, separator).trim();
        String endValue = rangeValue.substring(separator + 1).trim();
        if (startValue.isEmpty()) {
            return suffixRange(endValue, completeSize);
        }

        long requestedStart = parsePositiveLong(startValue, completeSize);
        Long requestedEnd = endValue.isEmpty() ? null : parsePositiveLong(endValue, completeSize);
        if (requestedEnd != null && requestedEnd < requestedStart) {
            throw new InvalidDownloadRangeException(completeSize);
        }

        return resolveStartRange(requestedStart, requestedEnd, completeSize);
    }

    private static FileDownloadRange suffixRange(String endValue, long completeSize) {
        long suffixLength = parsePositiveLong(endValue, completeSize);
        if (suffixLength <= 0 || completeSize <= 0) {
            throw new InvalidDownloadRangeException(completeSize);
        }

        long start = Math.max(completeSize - suffixLength, 0);
        long end = completeSize - 1;

        return new FileDownloadRange(start, end);
    }

    private static FileDownloadRange resolveStartRange(long requestedStart, Long requestedEnd, long completeSize) {
        if (requestedStart < 0 || requestedStart >= completeSize) {
            throw new InvalidDownloadRangeException(completeSize);
        }

        long end = requestedEnd == null ? completeSize - 1 : Math.min(requestedEnd, completeSize - 1);

        return new FileDownloadRange(requestedStart, end);
    }

    private static long parsePositiveLong(String value, long completeSize) {
        if (value == null || value.isBlank()) {
            throw new InvalidDownloadRangeException(completeSize);
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new InvalidDownloadRangeException(completeSize);
            }

            return parsed;
        } catch (NumberFormatException ex) {
            throw new InvalidDownloadRangeException(completeSize);
        }
    }
}
