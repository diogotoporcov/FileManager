ALTER TABLE processing_jobs DROP CONSTRAINT IF EXISTS processing_jobs_job_type_check;
ALTER TABLE processing_jobs
    ADD CONSTRAINT processing_jobs_job_type_check
        CHECK (job_type IN ('CHECKSUM', 'PHASH', 'EMBEDDING', 'VIDEO_ANALYSIS', 'AUDIO_ANALYSIS'));

CREATE TABLE audio_fingerprints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id) UNIQUE,
    duration_ms BIGINT NOT NULL CHECK (duration_ms > 0),
    codec VARCHAR(255) NOT NULL CHECK (length(trim(codec)) > 0),
    sample_rate INTEGER NOT NULL CHECK (sample_rate > 0),
    channels INTEGER NOT NULL CHECK (channels > 0),
    bit_rate BIGINT CHECK (bit_rate > 0),
    audio_stream_index INTEGER CHECK (audio_stream_index >= 0),
    container_format VARCHAR(255),
    fingerprint VARCHAR(32768) NOT NULL CHECK (length(trim(fingerprint)) > 0),
    fingerprint_algorithm VARCHAR(64) NOT NULL CHECK (length(trim(fingerprint_algorithm)) > 0),
    fingerprint_version VARCHAR(128) NOT NULL CHECK (length(trim(fingerprint_version)) > 0),
    fingerprint_duration_seconds INTEGER NOT NULL CHECK (fingerprint_duration_seconds > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audio_fingerprints_file ON audio_fingerprints(file_id);
