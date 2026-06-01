CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(255) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_subject)
);

CREATE TABLE organization_members (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL CHECK (role IN ('VIEWER', 'CONTRIBUTOR', 'EDITOR', 'MANAGER', 'ADMIN', 'OWNER')),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organization_id, user_id)
);

CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL UNIQUE,
    etag VARCHAR(255),
    mime_type VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    owner_user_id UUID REFERENCES users(id),
    owner_organization_id UUID REFERENCES organizations(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT owner_check CHECK (
        (owner_user_id IS NOT NULL AND owner_organization_id IS NULL) OR
        (owner_user_id IS NULL AND owner_organization_id IS NOT NULL)
    )
);

CREATE INDEX idx_files_owner_user_active_created
    ON files(owner_user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND owner_user_id IS NOT NULL;

CREATE INDEX idx_files_owner_organization_active_created
    ON files(owner_organization_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND owner_organization_id IS NOT NULL;

CREATE TABLE file_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id),
    algorithm VARCHAR(50) NOT NULL CHECK (algorithm IN ('SHA256')),
    hash_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, algorithm)
);

CREATE INDEX idx_fingerprints_value ON file_fingerprints(hash_value);
CREATE INDEX idx_fingerprints_file_algo ON file_fingerprints(file_id, algorithm);

CREATE TABLE image_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id) UNIQUE,
    phash VARCHAR(255) NOT NULL CHECK (phash ~ '^[0-9a-fA-F]{16}$'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_image_fingerprints_phash ON image_fingerprints(phash);

CREATE OR REPLACE FUNCTION hamming_distance_hex64(left_hash TEXT, right_hash TEXT)
RETURNS INTEGER
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    WITH decoded AS (
        SELECT decode(left_hash, 'hex') AS left_bytes,
               decode(right_hash, 'hex') AS right_bytes
    )
    SELECT COALESCE(SUM(bit_count((get_byte(left_bytes, byte_index) # get_byte(right_bytes, byte_index))::bit(8))), 0)::INTEGER
    FROM decoded, generate_series(0, 7) AS byte_offsets(byte_index);
$$;

CREATE TABLE file_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id),
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(255) NOT NULL,
    dimension INTEGER NOT NULL CHECK (dimension = 768),
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, model_name, model_version)
);

CREATE INDEX idx_embeddings_file_model ON file_embeddings(file_id, model_name, model_version);

CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id),
    job_type VARCHAR(255) NOT NULL CHECK (job_type IN ('CHECKSUM', 'PHASH', 'EMBEDDING')),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    external_job_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processing_jobs_file ON processing_jobs(file_id);
CREATE INDEX idx_processing_jobs_status ON processing_jobs(status);

CREATE TABLE duplicate_candidates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_file_id UUID NOT NULL REFERENCES files(id),
    candidate_file_id UUID NOT NULL REFERENCES files(id),
    detection_method VARCHAR(255) NOT NULL CHECK (detection_method IN ('EXACT', 'PHASH', 'EMBEDDING')),
    distance DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(source_file_id, candidate_file_id, detection_method),
    CONSTRAINT no_self_duplicate CHECK (source_file_id != candidate_file_id)
);

CREATE INDEX idx_duplicate_source ON duplicate_candidates(source_file_id);
CREATE INDEX idx_duplicate_candidate ON duplicate_candidates(candidate_file_id);
