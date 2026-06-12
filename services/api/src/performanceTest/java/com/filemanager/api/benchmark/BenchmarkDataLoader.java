package com.filemanager.api.benchmark;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
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
            copy(connection, "COPY image_fingerprints (id,file_id,phash,created_at) FROM STDIN WITH CSV",
                    dataset.imageFingerprints);
            copy(connection, "COPY file_embeddings (id,file_id,model_name,model_version,dimension,embedding,created_at)"
                    + " FROM STDIN WITH CSV", dataset.fileEmbeddings);
            copy(connection, "COPY audio_fingerprints (id,file_id,duration_ms,codec,sample_rate,channels,bit_rate,"
                    + "audio_stream_index,container_format,fingerprint,fingerprint_hash,fingerprint_algorithm,"
                    + "fingerprint_version,fingerprint_duration_seconds,created_at,updated_at) FROM STDIN WITH CSV",
                    dataset.audioFingerprints);
            copy(connection, "COPY video_embeddings (id,file_id,model_name,model_version,dimension,embedding,"
                    + "pooling_strategy,source_frame_count,created_at,updated_at) FROM STDIN WITH CSV",
                    dataset.videoEmbeddings);
            copy(connection, "COPY file_grants (id,file_id,grantee_user_id,permission,created_by_user_id,"
                    + "created_at,revoked_at) FROM STDIN WITH CSV", dataset.fileGrants);
            copy(connection, "COPY folder_grants (id,folder_id,grantee_user_id,permission,scope,created_by_user_id,"
                    + "created_at,revoked_at) FROM STDIN WITH CSV", dataset.folderGrants);
            copy(connection, "COPY processing_jobs (id,file_id,job_type,status,external_job_id,error_message,"
                    + "created_at,updated_at) FROM STDIN WITH CSV", dataset.processingJobs);
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
}
