package com.filemanager.api.benchmark;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.postgresql.PGConnection;

final class BenchmarkDataLoader {
    private static final int CHUNK_SIZE = 5_000;
    private final DataSource dataSource;

    BenchmarkDataLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void load(BenchmarkDatasetPlan dataset) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET session_replication_role = 'origin'");

            copy(connection, "COPY users (id,email,first_name,last_name,created_at,updated_at) FROM STDIN WITH CSV",
                    dataset.users);
            copy(connection, "COPY folders (id,name,parent_folder_id,owner_user_id,created_by_user_id,"
                    + "created_at,updated_at,deleted_at) FROM STDIN WITH CSV", dataset.folders);
            copy(connection, "COPY folder_closure (ancestor_folder_id,descendant_folder_id,depth) FROM STDIN WITH CSV",
                    dataset.folderClosures);
            copy(connection, "COPY files (id,name,storage_path,etag,mime_type,size,owner_user_id,folder_id,"
                    + "created_by_user_id,created_at,updated_at,deleted_at) FROM STDIN WITH CSV", dataset.files);
            copy(connection, "COPY file_fingerprints (id,file_id,algorithm,hash_value,created_at) FROM STDIN WITH CSV",
                    dataset.fileFingerprints);
            copy(connection, "COPY exact_duplicate_groups (id,owner_user_id,algorithm,hash_value,active_file_count,"
                    + "representative_file_id,created_at,updated_at) FROM STDIN WITH CSV",
                    dataset.exactDuplicateGroups);
            copy(connection, "COPY image_fingerprints (id,file_id,phash,created_at) FROM STDIN WITH CSV",
                    dataset.imageFingerprints);
            copy(connection, "COPY file_embeddings (id,file_id,model_name,model_version,dimension,embedding,created_at)"
                    + " FROM STDIN WITH CSV", dataset.fileEmbeddings);
            copy(connection, "COPY audio_fingerprints (id,file_id,duration_ms,codec,sample_rate,channels,bit_rate,"
                    + "audio_stream_index,container_format,fingerprint,fingerprint_hash,fingerprint_algorithm,"
                    + "fingerprint_version,fingerprint_duration_seconds,created_at,updated_at) FROM STDIN WITH CSV",
                    dataset.audioFingerprints);
        }
    }

    void load(BenchmarkDatasetPlan expectedDataset, Path datasetPath) throws Exception {
        BenchmarkDatasetArtifactStore artifactStore = new BenchmarkDatasetArtifactStore();

        artifactStore.validateDatasetPath(datasetPath, expectedDataset);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET session_replication_role = 'origin'");

            Map<String, Path> files = artifactStore.requiredCsvFiles(datasetPath);
            copyFile(connection, "COPY users (id,email,first_name,last_name,created_at,updated_at) FROM STDIN WITH CSV",
                    files.get("users"));
            copyFile(connection, "COPY folders (id,name,parent_folder_id,owner_user_id,created_by_user_id,"
                    + "created_at,updated_at,deleted_at) FROM STDIN WITH CSV", files.get("folders"));
            copyFile(connection, "COPY folder_closure (ancestor_folder_id,descendant_folder_id,depth) FROM STDIN WITH CSV",
                    files.get("folder_closure"));
            copyFile(connection, "COPY files (id,name,storage_path,etag,mime_type,size,owner_user_id,folder_id,"
                    + "created_by_user_id,created_at,updated_at,deleted_at) FROM STDIN WITH CSV", files.get("files"));
            copyFile(connection, "COPY file_fingerprints (id,file_id,algorithm,hash_value,created_at) FROM STDIN WITH CSV",
                    files.get("file_fingerprints"));
            copyFile(connection, "COPY image_fingerprints (id,file_id,phash,created_at) FROM STDIN WITH CSV",
                    files.get("image_fingerprints"));
            copyFile(connection, "COPY file_embeddings (id,file_id,model_name,model_version,dimension,embedding,created_at)"
                    + " FROM STDIN WITH CSV", files.get("file_embeddings"));
            copyFile(connection, "COPY audio_fingerprints (id,file_id,duration_ms,codec,sample_rate,channels,bit_rate,"
                    + "audio_stream_index,container_format,fingerprint,fingerprint_hash,fingerprint_algorithm,"
                    + "fingerprint_version,fingerprint_duration_seconds,created_at,updated_at) FROM STDIN WITH CSV",
                    files.get("audio_fingerprints"));
            copyFile(connection, "COPY exact_duplicate_groups (id,owner_user_id,algorithm,hash_value,active_file_count,"
                    + "representative_file_id,created_at,updated_at) FROM STDIN WITH CSV",
                    files.get("exact_duplicate_groups"));
        }
    }

    private void copy(Connection connection, String sql, List<? extends CsvWritable> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }

        PGConnection pgConnection = connection.unwrap(PGConnection.class);
        StringBuilder builder = new StringBuilder();
        int count = 0;

        for (CsvWritable row : rows) {
            builder.append(row.toCsv());
            count++;

            if (count % CHUNK_SIZE == 0) {
                pgConnection.getCopyAPI().copyIn(sql, new StringReader(builder.toString()));
                builder.setLength(0);
            }
        }

        if (!builder.isEmpty()) {
            pgConnection.getCopyAPI().copyIn(sql, new StringReader(builder.toString()));
        }
    }

    private void copyFile(Connection connection, String sql, Path path) throws Exception {
        if (Files.size(path) == 0) {
            return;
        }

        PGConnection pgConnection = connection.unwrap(PGConnection.class);

        try (var reader = Files.newBufferedReader(path)) {
            pgConnection.getCopyAPI().copyIn(sql, reader);
        }
    }
}
