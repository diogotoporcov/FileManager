package com.filemanager.api.benchmark;

import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

interface CsvWritable {
    String toCsv();
}

final class BenchmarkCsvRows {
    private BenchmarkCsvRows() {
    }

    static String row(Object... values) {
        try {
            StringWriter writer = new StringWriter();
            try (CSVPrinter printer = CSVFormat.DEFAULT.builder()
                    .setRecordSeparator('\n')
                    .get()
                    .print(writer)) {
                printer.printRecord(values);
            }

            return writer.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create benchmark COPY row", ex);
        }
    }
}

record UserRow(
        UUID id,
        String email,
        String firstName,
        String lastName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(id, email, firstName, lastName, createdAt, updatedAt);
    }
}

record FolderRow(
        UUID id,
        String name,
        UUID parentFolderId,
        UUID ownerUserId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(id, name, parentFolderId, ownerUserId, createdByUserId, createdAt, updatedAt, deletedAt);
    }
}

record FolderClosureRow(UUID ancestorFolderId, UUID descendantFolderId, int depth) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(ancestorFolderId, descendantFolderId, depth);
    }
}

record FileRow(
        UUID id,
        String name,
        String storagePath,
        String etag,
        String mimeType,
        long size,
        UUID ownerUserId,
        UUID folderId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(
                id,
                name,
                storagePath,
                etag,
                mimeType,
                size,
                ownerUserId,
                folderId,
                createdByUserId,
                createdAt,
                updatedAt,
                deletedAt);
    }
}

record FileFingerprintRow(
        UUID id,
        UUID fileId,
        String algorithm,
        String hashValue,
        OffsetDateTime createdAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(id, fileId, algorithm, hashValue, createdAt);
    }
}

record ExactDuplicateGroupRow(
        UUID id,
        UUID ownerUserId,
        String algorithm,
        String hashValue,
        long activeFileCount,
        UUID representativeFileId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(
                id,
                ownerUserId,
                algorithm,
                hashValue,
                activeFileCount,
                representativeFileId,
                createdAt,
                updatedAt);
    }
}

record ImageFingerprintRow(UUID id, UUID fileId, String phash, OffsetDateTime createdAt)
        implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(id, fileId, phash, createdAt);
    }
}

record FileEmbeddingRow(
        UUID id,
        UUID fileId,
        String modelName,
        String modelVersion,
        int dimension,
        String embedding,
        OffsetDateTime createdAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(id, fileId, modelName, modelVersion, dimension, embedding, createdAt);
    }
}

record AudioFingerprintRow(
        UUID id,
        UUID fileId,
        long durationMs,
        String codec,
        int sampleRate,
        int channels,
        long bitRate,
        int audioStreamIndex,
        String containerFormat,
        String fingerprint,
        String fingerprintHash,
        String fingerprintAlgorithm,
        String fingerprintVersion,
        int fingerprintDurationSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) implements CsvWritable {
    @Override
    public String toCsv() {
        return BenchmarkCsvRows.row(
                id,
                fileId,
                durationMs,
                codec,
                sampleRate,
                channels,
                bitRate,
                audioStreamIndex,
                containerFormat,
                fingerprint,
                fingerprintHash,
                fingerprintAlgorithm,
                fingerprintVersion,
                fingerprintDurationSeconds,
                createdAt,
                updatedAt);
    }
}
