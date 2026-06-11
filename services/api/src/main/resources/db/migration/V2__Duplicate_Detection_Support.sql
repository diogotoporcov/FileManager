CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION filemanager_hex_hamming_distance(left_hex TEXT, right_hex TEXT)
RETURNS INTEGER
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    position_index INTEGER;
    left_value INTEGER;
    right_value INTEGER;
    xor_value INTEGER;
    distance INTEGER := 0;
BEGIN
    IF left_hex !~ '^[0-9a-fA-F]{16}$' OR right_hex !~ '^[0-9a-fA-F]{16}$' THEN
        RAISE EXCEPTION 'pHash values must be 16-character hexadecimal strings';
    END IF;

    FOR position_index IN 1..16 LOOP
        left_value := strpos('0123456789abcdef', lower(substr(left_hex, position_index, 1))) - 1;
        right_value := strpos('0123456789abcdef', lower(substr(right_hex, position_index, 1))) - 1;
        xor_value := left_value # right_value;
        distance := distance + CASE xor_value
            WHEN 0 THEN 0
            WHEN 1 THEN 1
            WHEN 2 THEN 1
            WHEN 3 THEN 2
            WHEN 4 THEN 1
            WHEN 5 THEN 2
            WHEN 6 THEN 2
            WHEN 7 THEN 3
            WHEN 8 THEN 1
            WHEN 9 THEN 2
            WHEN 10 THEN 2
            WHEN 11 THEN 3
            WHEN 12 THEN 2
            WHEN 13 THEN 3
            WHEN 14 THEN 3
            WHEN 15 THEN 4
            ELSE 0
        END;
    END LOOP;

    RETURN distance;
END;
$$;

ALTER TABLE audio_fingerprints
    ADD COLUMN fingerprint_hash VARCHAR(64);

UPDATE audio_fingerprints
SET fingerprint_hash = encode(digest(trim(fingerprint), 'sha256'), 'hex')
WHERE fingerprint_hash IS NULL;

ALTER TABLE audio_fingerprints
    ALTER COLUMN fingerprint_hash SET NOT NULL;

CREATE INDEX idx_audio_fingerprints_hash
    ON audio_fingerprints(fingerprint_hash);

CREATE INDEX idx_file_fingerprints_algorithm_hash
    ON file_fingerprints(algorithm, hash_value);

CREATE INDEX idx_files_owner_deleted_folder
    ON files(owner_user_id, deleted_at, folder_id);

CREATE INDEX idx_file_embeddings_model_version_dimension
    ON file_embeddings(model_name, model_version, dimension);

CREATE TABLE video_embeddings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id) UNIQUE,
    model_name VARCHAR(255) NOT NULL CHECK (length(trim(model_name)) > 0),
    model_version VARCHAR(255) NOT NULL CHECK (length(trim(model_version)) > 0),
    dimension INTEGER NOT NULL CHECK (dimension = 768),
    embedding vector(768) NOT NULL,
    pooling_strategy VARCHAR(64) NOT NULL CHECK (length(trim(pooling_strategy)) > 0),
    source_frame_count INTEGER NOT NULL CHECK (source_frame_count > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_video_embeddings_model_version_dimension
    ON video_embeddings(model_name, model_version, dimension);
