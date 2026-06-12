package com.filemanager.api.benchmark;

import static com.filemanager.api.benchmark.BenchmarkSupport.id;
import static com.filemanager.api.benchmark.BenchmarkSupport.sha256;

import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class BenchmarkDatasetGenerator {

    static final String MODEL_NAME = "openai/clip-vit-large-patch14";
    static final String MODEL_VERSION = "1";
    private static final int EMBEDDING_DIMENSION = 768;
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 6, 11, 12, 0, 0, 0, ZoneOffset.UTC);
    private final BenchmarkOptions options;
    private final BenchmarkDatasetPlan dataset;

    BenchmarkDatasetGenerator(BenchmarkOptions options) {
        this.options = options;
        this.dataset = new BenchmarkDatasetPlan(options, id(options.seed(), "user-0"));
    }

    BenchmarkDatasetPlan generate() {
        addUsers();
        addFolders();
        addDuplicateFixtures();
        addFillerRecords();

        return dataset;
    }

    private void addUsers() {
        for (int index = 0; index < 5; index++) {
            UUID userId = id(options.seed(), "user-" + index);

            dataset.users.add(new UserRow(
                    userId,
                    "benchmark-user-" + index + "@example.invalid",
                    "Benchmark",
                    "User" + index,
                    CREATED_AT,
                    CREATED_AT));
        }
    }

    private void addFolders() {
        UUID actor = dataset.actorUserId();
        UUID activeFolder = id(options.seed(), "folder-active");
        UUID deletedFolder = id(options.seed(), "folder-deleted");

        dataset.folders.add(new FolderRow(activeFolder, "benchmark-active", null, actor, actor, CREATED_AT, CREATED_AT, null));
        dataset.folders.add(new FolderRow(
                deletedFolder,
                "benchmark-deleted",
                null,
                actor,
                actor,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT.plusDays(1)));

        dataset.folderClosures.add(new FolderClosureRow(activeFolder, activeFolder, 0));
        dataset.folderClosures.add(new FolderClosureRow(deletedFolder, deletedFolder, 0));
    }

    private void addDuplicateFixtures() {
        addExactFixtures();
        addImagePhashFixtures();
        addImageEmbeddingFixtures();
        addAudioFixtures();
        addVideoFixtures();
        addProcessingFixture();
    }

    private void addExactFixtures() {
        UUID actor = dataset.actorUserId();
        UUID other = id(options.seed(), "user-1");
        UUID deletedFolder = id(options.seed(), "folder-deleted");
        String hash = sha256("exact-one-match-" + options.seed());
        FileRow source = addFile("exact-one-match", actor, null, "text/plain", null);
        FileRow match = addFile("exact-one-match-copy", actor, null, "text/plain", null);
        FileRow deleted = addFile("deleted-exact", actor, null, "text/plain", CREATED_AT.plusDays(1));
        FileRow inDeletedFolder = addFile("deleted-folder-exact", actor, deletedFolder, "text/plain", null);
        FileRow foreign = addFile("foreign-exact", other, null, "text/plain", null);
        FileRow foreignShared = addFile("foreign-shared-exact", other, null, "text/plain", null);

        for (FileRow file : List.of(source, match, deleted, inDeletedFolder, foreign, foreignShared)) {
            addExactFingerprint(file.id(), hash);
        }

        dataset.fileGrants.add(new FileGrantRow(
                id(options.seed(), "grant-foreign-shared-exact"),
                foreignShared.id(),
                actor,
                "FILE_VIEW",
                other,
                CREATED_AT,
                null));

        dataset.sources.put("exact-one-match", source.id());
        dataset.sources.put("foreign-shared-exact", foreignShared.id());
        dataset.hashes.put("exact-one-match", hash);
        dataset.cases.add(new BenchmarkCase(
                "duplicate.search.EXACT.one-match",
                source.id(),
                DuplicateSearchMethod.EXACT,
                hash,
                List.of(match.id())));

        FileRow noMatch = addFile("exact-no-match", actor, null, "text/plain", null);
        String noMatchHash = sha256("exact-no-match-" + options.seed());

        addExactFingerprint(noMatch.id(), noMatchHash);

        dataset.sources.put("exact-no-match", noMatch.id());
        dataset.hashes.put("exact-no-match", noMatchHash);
        dataset.cases.add(new BenchmarkCase(
                "duplicate.search.EXACT.no-match",
                noMatch.id(),
                DuplicateSearchMethod.EXACT,
                noMatchHash,
                List.of()));

        String groupHash = sha256("large-exact-group-" + options.seed());

        for (int index = 0; index < 20; index++) {
            addExactFingerprint(addFile("large-exact-group-" + index, actor, null, "text/plain", null).id(), groupHash);
        }
    }

    private void addImagePhashFixtures() {
        UUID actor = dataset.actorUserId();
        UUID other = id(options.seed(), "user-1");
        FileRow source = addFile("image-phash-source", actor, null, "image/png", null);
        FileRow identical = addFile("image-phash-identical", actor, null, "image/png", null);
        FileRow inside = addFile("image-phash-inside", actor, null, "image/png", null);
        FileRow atThreshold = addFile("image-phash-at-threshold", actor, null, "image/png", null);
        FileRow outside = addFile("image-phash-outside", actor, null, "image/png", null);
        FileRow foreign = addFile("image-phash-foreign", other, null, "image/png", null);

        addImageFingerprint(source.id(), phashWithDistance(0));
        addImageFingerprint(identical.id(), phashWithDistance(0));
        addImageFingerprint(inside.id(), phashWithDistance(5));
        addImageFingerprint(atThreshold.id(), phashWithDistance(10));
        addImageFingerprint(outside.id(), phashWithDistance(11));
        addImageFingerprint(foreign.id(), phashWithDistance(0));

        dataset.sources.put("image-phash-threshold", source.id());
        dataset.cases.add(new BenchmarkCase(
                "duplicate.search.IMAGE_PHASH.threshold",
                source.id(),
                DuplicateSearchMethod.IMAGE_PHASH,
                null,
                List.of(identical.id(), inside.id(), atThreshold.id())));
    }

    private void addImageEmbeddingFixtures() {
        UUID actor = dataset.actorUserId();
        UUID other = id(options.seed(), "user-1");
        FileRow source = addFile("image-embedding-source", actor, null, "image/jpeg", null);
        FileRow inside = addFile("image-embedding-inside", actor, null, "image/jpeg", null);
        FileRow atThreshold = addFile("image-embedding-at-threshold", actor, null, "image/jpeg", null);
        FileRow outside = addFile("image-embedding-outside", actor, null, "image/jpeg", null);
        FileRow wrongModel = addFile("image-embedding-wrong-model", actor, null, "image/jpeg", null);
        FileRow foreign = addFile("image-embedding-foreign", other, null, "image/jpeg", null);

        addFileEmbedding(source.id(), vectorAtDistance(0.0), MODEL_NAME, MODEL_VERSION);
        addFileEmbedding(inside.id(), vectorAtDistance(0.10), MODEL_NAME, MODEL_VERSION);
        addFileEmbedding(atThreshold.id(), vectorAtDistance(0.20), MODEL_NAME, MODEL_VERSION);
        addFileEmbedding(outside.id(), vectorAtDistance(0.21), MODEL_NAME, MODEL_VERSION);
        addFileEmbedding(wrongModel.id(), vectorAtDistance(0.0), MODEL_NAME, "2");
        addFileEmbedding(foreign.id(), vectorAtDistance(0.0), MODEL_NAME, MODEL_VERSION);

        dataset.sources.put("image-embedding-threshold", source.id());
        dataset.cases.add(new BenchmarkCase(
                "duplicate.search.IMAGE_EMBEDDING.threshold",
                source.id(),
                DuplicateSearchMethod.IMAGE_EMBEDDING,
                null,
                List.of(inside.id(), atThreshold.id())));
    }

    private void addAudioFixtures() {
        UUID actor = dataset.actorUserId();
        UUID other = id(options.seed(), "user-1");
        String fingerprint = "audio-fixture-" + options.seed();
        FileRow source = addFile("audio-source", actor, null, "audio/mpeg", null);
        FileRow match = addFile("audio-match", actor, null, "audio/wav", null);
        FileRow videoWithAudio = addFile("video-with-audio", actor, null, "video/mp4", null);
        FileRow foreign = addFile("audio-foreign", other, null, "audio/mpeg", null);

        for (FileRow file : List.of(source, match, videoWithAudio, foreign)) {
            addAudioFingerprint(file.id(), fingerprint, "chromaprint", "fpcalc-v1");
        }

        dataset.sources.put("audio-one-match", source.id());
        dataset.cases.add(new BenchmarkCase(
                "duplicate.search.AUDIO_FINGERPRINT.one-match",
                source.id(),
                DuplicateSearchMethod.AUDIO_FINGERPRINT,
                null,
                List.of(match.id())));

        String groupFingerprint = "audio-large-group-" + options.seed();

        for (int index = 0; index < 3; index++) {
            addAudioFingerprint(
                    addFile("audio-group-" + index, actor, null, "audio/mpeg", null).id(),
                    groupFingerprint,
                    "chromaprint",
                    "fpcalc-v1");
        }
    }

    private void addVideoFixtures() {
        UUID actor = dataset.actorUserId();
        UUID other = id(options.seed(), "user-1");
        FileRow source = addFile("video-source", actor, null, "video/mp4", null);
        FileRow inside = addFile("video-inside", actor, null, "video/mp4", null);
        FileRow outside = addFile("video-outside", actor, null, "video/mp4", null);
        FileRow foreign = addFile("video-foreign", other, null, "video/mp4", null);

        addVideoEmbedding(source.id(), vectorAtDistance(0.0), MODEL_NAME, MODEL_VERSION);
        addVideoEmbedding(inside.id(), vectorAtDistance(0.10), MODEL_NAME, MODEL_VERSION);
        addVideoEmbedding(outside.id(), vectorAtDistance(0.21), MODEL_NAME, MODEL_VERSION);
        addVideoEmbedding(foreign.id(), vectorAtDistance(0.0), MODEL_NAME, MODEL_VERSION);

        dataset.sources.put("video-embedding-one-match", source.id());
        dataset.cases.add(new BenchmarkCase(
                "duplicate.search.VIDEO_EMBEDDING.one-match",
                source.id(),
                DuplicateSearchMethod.VIDEO_EMBEDDING,
                null,
                List.of(inside.id())));
    }

    private void addProcessingFixture() {
        UUID actor = dataset.actorUserId();
        FileRow file = addFile("processing-status", actor, null, "image/png", null);

        dataset.sources.put("processing-status", file.id());

        for (String jobType : List.of("CHECKSUM", "PHASH", "EMBEDDING")) {
            dataset.processingJobs.add(new ProcessingJobRow(
                    id(options.seed(), "processing-" + jobType),
                    file.id(),
                    jobType,
                    "COMPLETED",
                    null,
                    null,
                    CREATED_AT,
                    CREATED_AT));
        }
    }

    private void addFillerRecords() {
        int index = 0;

        while (dataset.files.size() < options.records()) {
            UUID owner = index % 10 == 0 ? id(options.seed(), "user-1") : dataset.actorUserId();
            String mimeType = switch (index % 10) {
                case 0, 1 -> "image/png";
                case 2, 3 -> "audio/mpeg";
                case 4, 5 -> "video/mp4";
                default -> "text/plain";
            };
            FileRow file = addFile("filler-" + index, owner, id(options.seed(), "folder-active"), mimeType, null);

            addExactFingerprint(file.id(), sha256("filler-" + options.seed() + "-" + index));

            index++;
        }
    }

    private FileRow addFile(String label, UUID ownerUserId, UUID folderId, String mimeType, OffsetDateTime deletedAt) {
        UUID fileId = id(options.seed(), "file-" + label);
        FileRow file = new FileRow(
                fileId,
                label + extension(mimeType),
                "benchmark/" + options.seed() + "/" + label,
                "etag-" + label,
                mimeType,
                4096L + dataset.files.size(),
                ownerUserId,
                folderId,
                ownerUserId,
                CREATED_AT.plusSeconds(dataset.files.size()),
                CREATED_AT.plusSeconds(dataset.files.size()),
                deletedAt);

        dataset.files.add(file);

        return file;
    }

    private void addExactFingerprint(UUID fileId, String hash) {
        dataset.fileFingerprints.add(new FileFingerprintRow(
                id(options.seed(), "fingerprint-" + fileId),
                fileId,
                "SHA256",
                hash,
                CREATED_AT));
    }

    private void addImageFingerprint(UUID fileId, String phash) {
        dataset.imageFingerprints.add(new ImageFingerprintRow(
                id(options.seed(), "image-fingerprint-" + fileId),
                fileId,
                phash,
                CREATED_AT));
    }

    private void addFileEmbedding(UUID fileId, float[] embedding, String modelName, String modelVersion) {
        dataset.fileEmbeddings.add(new FileEmbeddingRow(
                id(options.seed(), "file-embedding-" + fileId + modelVersion),
                fileId,
                modelName,
                modelVersion,
                EMBEDDING_DIMENSION,
                vectorLiteral(embedding),
                CREATED_AT));
    }

    private void addVideoEmbedding(UUID fileId, float[] embedding, String modelName, String modelVersion) {
        dataset.videoEmbeddings.add(new VideoEmbeddingRow(
                id(options.seed(), "video-embedding-" + fileId + modelVersion),
                fileId,
                modelName,
                modelVersion,
                EMBEDDING_DIMENSION,
                vectorLiteral(embedding),
                "mean",
                10,
                CREATED_AT,
                CREATED_AT));
    }

    private void addAudioFingerprint(UUID fileId, String fingerprint, String algorithm, String version) {
        dataset.audioFingerprints.add(new AudioFingerprintRow(
                id(options.seed(), "audio-fingerprint-" + fileId),
                fileId,
                4000L,
                "aac",
                44100,
                2,
                128000L,
                0,
                "mp4",
                fingerprint,
                sha256(fingerprint.trim()),
                algorithm,
                version,
                4,
                CREATED_AT,
                CREATED_AT));
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav" -> ".wav";
            case "video/mp4" -> ".mp4";
            default -> ".txt";
        };
    }

    private String phashWithDistance(int distance) {
        long value = distance == 64 ? -1L : (1L << distance) - 1L;

        return String.format(Locale.ROOT, "%016x", value);
    }

    private float[] vectorAtDistance(double distance) {
        float[] vector = new float[EMBEDDING_DIMENSION];
        double cosine = 1.0 - distance;

        vector[0] = (float) cosine;
        vector[1] = (float) Math.sqrt(Math.max(0.0, 1.0 - cosine * cosine));

        return vector;
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");

        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }

            builder.append(String.format(Locale.ROOT, "%.9f", vector[index]));
        }

        return builder.append(']').toString();
    }
}
