CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    display_name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(20) NOT NULL CHECK (scope_type IN ('OWNER', 'FOLDER')),
    scope_folder_id UUID REFERENCES folders(id),
    owner_user_id UUID REFERENCES users(id),
    owner_organization_id UUID REFERENCES organizations(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT tag_owner_check CHECK (
        (owner_user_id IS NOT NULL AND owner_organization_id IS NULL) OR
        (owner_user_id IS NULL AND owner_organization_id IS NOT NULL)
    ),
    CONSTRAINT tag_scope_folder_check CHECK (
        (scope_type = 'OWNER' AND scope_folder_id IS NULL) OR
        (scope_type = 'FOLDER' AND scope_folder_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_tags_owner_user_active_normalized
    ON tags(owner_user_id, scope_type, normalized_name)
    WHERE deleted_at IS NULL AND owner_user_id IS NOT NULL AND scope_type = 'OWNER';

CREATE UNIQUE INDEX ux_tags_owner_organization_active_normalized
    ON tags(owner_organization_id, scope_type, normalized_name)
    WHERE deleted_at IS NULL AND owner_organization_id IS NOT NULL AND scope_type = 'OWNER';

CREATE UNIQUE INDEX ux_tags_scope_folder_active_normalized
    ON tags(scope_folder_id, normalized_name)
    WHERE deleted_at IS NULL AND scope_type = 'FOLDER';

CREATE INDEX idx_tags_owner_user_scope_normalized
    ON tags(owner_user_id, scope_type, normalized_name)
    WHERE owner_user_id IS NOT NULL;

CREATE INDEX idx_tags_owner_organization_scope_normalized
    ON tags(owner_organization_id, scope_type, normalized_name)
    WHERE owner_organization_id IS NOT NULL;

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
