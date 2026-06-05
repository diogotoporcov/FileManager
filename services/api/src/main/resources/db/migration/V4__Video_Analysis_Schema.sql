ALTER TABLE processing_jobs DROP CONSTRAINT IF EXISTS processing_jobs_job_type_check;
ALTER TABLE processing_jobs
    ADD CONSTRAINT processing_jobs_job_type_check
        CHECK (job_type IN ('CHECKSUM', 'PHASH', 'EMBEDDING', 'VIDEO_ANALYSIS'));

CREATE TABLE video_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id) UNIQUE,
    duration_ms BIGINT NOT NULL CHECK (duration_ms > 0),
    width INTEGER CHECK (width > 0),
    height INTEGER CHECK (height > 0),
    frame_count BIGINT CHECK (frame_count >= 0),
    codec VARCHAR(255),
    sampled_frame_count INTEGER NOT NULL CHECK (sampled_frame_count > 0),
    sampling_strategy VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_video_fingerprints_file ON video_fingerprints(file_id);

CREATE TABLE video_frame_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id),
    timestamp_ms BIGINT NOT NULL CHECK (timestamp_ms >= 0),
    frame_index INTEGER NOT NULL CHECK (frame_index >= 0),
    phash VARCHAR(255) NOT NULL CHECK (phash ~ '^[0-9a-fA-F]{16}$'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, frame_index)
);

CREATE INDEX idx_video_frame_fingerprints_file ON video_frame_fingerprints(file_id);
CREATE INDEX idx_video_frame_fingerprints_phash ON video_frame_fingerprints(phash);

CREATE TABLE video_frame_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id),
    timestamp_ms BIGINT NOT NULL CHECK (timestamp_ms >= 0),
    frame_index INTEGER NOT NULL CHECK (frame_index >= 0),
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(255) NOT NULL,
    dimension INTEGER NOT NULL CHECK (dimension = 768),
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, frame_index, model_name, model_version)
);

CREATE INDEX idx_video_frame_embeddings_file_model
    ON video_frame_embeddings(file_id, model_name, model_version);
