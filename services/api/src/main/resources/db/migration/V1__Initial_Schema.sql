-- PostgreSQL extensions.
CREATE EXTENSION IF NOT EXISTS vector;
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

-- Identity tables.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(255) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_subject)
);

-- Folder hierarchy and lookup indexes.
CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    parent_folder_id UUID REFERENCES folders(id),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_folders_owner_user_parent_active_name
    ON folders(owner_user_id, parent_folder_id, deleted_at, name);

CREATE UNIQUE INDEX ux_folders_owner_user_active_root_name
    ON folders(owner_user_id, lower(name))
    WHERE parent_folder_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX ux_folders_parent_active_child_name
    ON folders(parent_folder_id, lower(name))
    WHERE parent_folder_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE folder_closure (
    ancestor_folder_id UUID NOT NULL REFERENCES folders(id),
    descendant_folder_id UUID NOT NULL REFERENCES folders(id),
    depth INTEGER NOT NULL,
    PRIMARY KEY (ancestor_folder_id, descendant_folder_id),
    CONSTRAINT chk_folder_closure_depth CHECK (depth >= 0)
);

CREATE INDEX idx_folder_closure_descendant
    ON folder_closure(descendant_folder_id);

CREATE INDEX idx_folder_closure_ancestor
    ON folder_closure(ancestor_folder_id);

CREATE INDEX idx_folder_closure_descendant_ancestor
    ON folder_closure(descendant_folder_id, ancestor_folder_id);

CREATE INDEX idx_folder_closure_ancestor_descendant
    ON folder_closure(ancestor_folder_id, descendant_folder_id);

-- File metadata and storage references.
CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL UNIQUE,
    etag VARCHAR(255),
    mime_type VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    folder_id UUID REFERENCES folders(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_files_owner_user_active_created
    ON files(owner_user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_files_folder_active_created
    ON files(folder_id, deleted_at, created_at DESC, id DESC);

CREATE INDEX idx_files_owner_deleted_folder
    ON files(owner_user_id, deleted_at, folder_id);

CREATE INDEX idx_files_created_by_user
    ON files(created_by_user_id);

-- File fingerprinting and embedding data.
CREATE TABLE file_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id),
    algorithm VARCHAR(50) NOT NULL CHECK (algorithm IN ('SHA256')),
    hash_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, algorithm)
);

CREATE INDEX idx_fingerprints_value ON file_fingerprints(hash_value);
CREATE INDEX idx_fingerprints_file_algo ON file_fingerprints(file_id, algorithm);
CREATE INDEX idx_file_fingerprints_algorithm_hash ON file_fingerprints(algorithm, hash_value);

CREATE TABLE image_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id) UNIQUE,
    phash VARCHAR(255) NOT NULL CHECK (phash ~ '^[0-9a-fA-F]{16}$'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_image_fingerprints_phash ON image_fingerprints(phash);

CREATE TABLE image_phash_mih_chunks (
    file_id UUID NOT NULL,
    chunk_index SMALLINT NOT NULL,
    chunk_value INTEGER NOT NULL,
    PRIMARY KEY (file_id, chunk_index),
    CONSTRAINT fk_image_phash_mih_chunks_file
        FOREIGN KEY (file_id)
        REFERENCES files(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_image_phash_mih_chunk_index
        CHECK (chunk_index BETWEEN 0 AND 2),
    CONSTRAINT chk_image_phash_mih_chunk_value
        CHECK (
            (
                chunk_index = 0
                AND chunk_value BETWEEN 0 AND 4194303
            )
            OR
            (
                chunk_index IN (1, 2)
                AND chunk_value BETWEEN 0 AND 2097151
            )
        )
);

CREATE INDEX idx_image_phash_mih_chunks_lookup
    ON image_phash_mih_chunks(chunk_index, chunk_value, file_id);

CREATE TABLE file_embeddings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id),
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(255) NOT NULL,
    dimension INTEGER NOT NULL CHECK (dimension = 768),
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, model_name, model_version)
);

CREATE INDEX idx_embeddings_file_model ON file_embeddings(file_id, model_name, model_version);

CREATE INDEX idx_file_embeddings_model_version_dimension
    ON file_embeddings(model_name, model_version, dimension);

CREATE INDEX idx_file_embeddings_embedding_hnsw_cosine
    ON file_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Background processing.
CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id),
    job_type VARCHAR(255) NOT NULL CHECK (job_type IN ('CHECKSUM', 'PHASH', 'EMBEDDING', 'AUDIO_ANALYSIS')),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    external_job_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processing_jobs_file ON processing_jobs(file_id);
CREATE INDEX idx_processing_jobs_status ON processing_jobs(status);

-- Tags.
CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    display_name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(20) NOT NULL CHECK (scope_type IN ('OWNER', 'FOLDER')),
    scope_folder_id UUID REFERENCES folders(id),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT tag_scope_folder_check CHECK (
        (scope_type = 'OWNER' AND scope_folder_id IS NULL) OR
        (scope_type = 'FOLDER' AND scope_folder_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_tags_owner_user_active_normalized
    ON tags(owner_user_id, scope_type, normalized_name)
    WHERE deleted_at IS NULL AND scope_type = 'OWNER';

CREATE UNIQUE INDEX ux_tags_scope_folder_active_normalized
    ON tags(scope_folder_id, normalized_name)
    WHERE deleted_at IS NULL AND scope_type = 'FOLDER';

CREATE INDEX idx_tags_owner_user_scope_normalized
    ON tags(owner_user_id, scope_type, normalized_name);

CREATE INDEX idx_tags_scope_folder_normalized
    ON tags(scope_folder_id, normalized_name)
    WHERE scope_folder_id IS NOT NULL;

CREATE INDEX idx_tags_created_by_user
    ON tags(created_by_user_id);

CREATE TABLE file_tags (
    file_id UUID NOT NULL REFERENCES files(id),
    tag_id UUID NOT NULL REFERENCES tags(id),
    tagged_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id, tag_id)
);

CREATE INDEX idx_file_tags_tag_file
    ON file_tags(tag_id, file_id);

CREATE INDEX idx_file_tags_file_tag
    ON file_tags(file_id, tag_id);

CREATE TABLE folder_tags (
    folder_id UUID NOT NULL REFERENCES folders(id),
    tag_id UUID NOT NULL REFERENCES tags(id),
    tagged_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (folder_id, tag_id)
);

CREATE INDEX idx_folder_tags_tag_folder
    ON folder_tags(tag_id, folder_id);

CREATE INDEX idx_folder_tags_folder_tag
    ON folder_tags(folder_id, tag_id);

-- Direct sharing and permission grants.
CREATE TABLE file_grants (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id),
    grantee_user_id UUID NOT NULL REFERENCES users(id),
    permission VARCHAR(255) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_file_grants_permission CHECK (permission IN ('FILE_VIEW', 'FILE_MODIFY', 'FILE_DELETE')),
    CONSTRAINT file_grants_no_self_grant CHECK (grantee_user_id <> created_by_user_id)
);

CREATE UNIQUE INDEX ux_file_grants_active_permission
    ON file_grants(file_id, grantee_user_id, permission)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_file_grants_file_grantee_active
    ON file_grants(file_id, grantee_user_id, revoked_at);

CREATE INDEX idx_file_grants_grantee_active
    ON file_grants(grantee_user_id, revoked_at);

CREATE TABLE folder_grants (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    folder_id UUID NOT NULL REFERENCES folders(id),
    grantee_user_id UUID NOT NULL REFERENCES users(id),
    permission VARCHAR(255) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_folder_grants_permission CHECK (permission IN ('FOLDER_VIEW', 'FOLDER_CREATE', 'FOLDER_RENAME', 'FOLDER_DELETE', 'FOLDER_UPLOAD_FILE')),
    CONSTRAINT chk_folder_grants_scope CHECK (scope IN ('DIRECT', 'RECURSIVE')),
    CONSTRAINT folder_grants_no_self_grant CHECK (grantee_user_id <> created_by_user_id)
);

CREATE UNIQUE INDEX ux_folder_grants_active_permission
    ON folder_grants(folder_id, grantee_user_id, permission)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_folder_grants_folder_grantee_active
    ON folder_grants(folder_id, grantee_user_id, revoked_at);

CREATE INDEX idx_folder_grants_grantee_active
    ON folder_grants(grantee_user_id, revoked_at);

-- Fingerprinting and embeddings.
CREATE TABLE audio_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    file_id UUID NOT NULL REFERENCES files(id) UNIQUE,
    duration_ms BIGINT NOT NULL CHECK (duration_ms > 0),
    codec VARCHAR(255) NOT NULL CHECK (length(trim(codec)) > 0),
    sample_rate INTEGER NOT NULL CHECK (sample_rate > 0),
    channels INTEGER NOT NULL CHECK (channels > 0),
    bit_rate BIGINT CHECK (bit_rate > 0),
    audio_stream_index INTEGER CHECK (audio_stream_index >= 0),
    container_format VARCHAR(255),
    fingerprint VARCHAR(32768) NOT NULL CHECK (length(trim(fingerprint)) > 0),
    fingerprint_hash VARCHAR(64) NOT NULL,
    fingerprint_algorithm VARCHAR(64) NOT NULL CHECK (length(trim(fingerprint_algorithm)) > 0),
    fingerprint_version VARCHAR(128) NOT NULL CHECK (length(trim(fingerprint_version)) > 0),
    fingerprint_duration_seconds INTEGER NOT NULL CHECK (fingerprint_duration_seconds > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audio_fingerprints_file ON audio_fingerprints(file_id);
CREATE INDEX idx_audio_fingerprints_hash ON audio_fingerprints(fingerprint_hash);

CREATE TABLE exact_duplicate_groups (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    algorithm VARCHAR(50) NOT NULL CHECK (algorithm IN ('SHA256')),
    hash_value VARCHAR(255) NOT NULL,
    active_file_count BIGINT NOT NULL CHECK (active_file_count >= 0),
    representative_file_id UUID REFERENCES files(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exact_duplicate_groups_owner_algorithm_hash
        UNIQUE(owner_user_id, algorithm, hash_value)
);

CREATE INDEX idx_exact_duplicate_groups_owner_count_algorithm_hash
    ON exact_duplicate_groups(owner_user_id, active_file_count DESC, algorithm, hash_value);
