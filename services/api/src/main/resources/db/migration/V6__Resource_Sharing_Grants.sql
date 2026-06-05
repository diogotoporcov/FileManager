CREATE TABLE file_grants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    file_id UUID NOT NULL REFERENCES files(id),
    grantee_user_id UUID NOT NULL REFERENCES users(id),
    permission VARCHAR(255) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE folder_grants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    folder_id UUID NOT NULL REFERENCES folders(id),
    grantee_user_id UUID NOT NULL REFERENCES users(id),
    permission VARCHAR(255) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_file_grants_active_permission
    ON file_grants(file_id, grantee_user_id, permission)
    WHERE revoked_at IS NULL;

CREATE UNIQUE INDEX ux_folder_grants_active_permission
    ON folder_grants(folder_id, grantee_user_id, permission)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_file_grants_file_grantee_active
    ON file_grants(file_id, grantee_user_id, revoked_at);

CREATE INDEX idx_folder_grants_folder_grantee_active
    ON folder_grants(folder_id, grantee_user_id, revoked_at);

CREATE INDEX idx_file_grants_grantee_active
    ON file_grants(grantee_user_id, revoked_at);

CREATE INDEX idx_folder_grants_grantee_active
    ON folder_grants(grantee_user_id, revoked_at);
