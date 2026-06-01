CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    parent_folder_id UUID REFERENCES folders(id),
    owner_user_id UUID REFERENCES users(id),
    owner_organization_id UUID REFERENCES organizations(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT folder_owner_check CHECK (
        (owner_user_id IS NOT NULL AND owner_organization_id IS NULL) OR
        (owner_user_id IS NULL AND owner_organization_id IS NOT NULL)
    )
);

ALTER TABLE files
    ADD COLUMN folder_id UUID REFERENCES folders(id),
    ADD COLUMN created_by_user_id UUID REFERENCES users(id);

CREATE INDEX idx_folders_owner_user_parent_active_name
    ON folders(owner_user_id, parent_folder_id, deleted_at, name)
    WHERE owner_user_id IS NOT NULL;

CREATE INDEX idx_folders_owner_organization_parent_active_name
    ON folders(owner_organization_id, parent_folder_id, deleted_at, name)
    WHERE owner_organization_id IS NOT NULL;

CREATE UNIQUE INDEX ux_folders_owner_user_active_sibling_name
    ON folders(owner_user_id, COALESCE(parent_folder_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE deleted_at IS NULL AND owner_user_id IS NOT NULL;

CREATE UNIQUE INDEX ux_folders_owner_organization_active_sibling_name
    ON folders(owner_organization_id, COALESCE(parent_folder_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE deleted_at IS NULL AND owner_organization_id IS NOT NULL;

CREATE INDEX idx_files_folder_active_created
    ON files(folder_id, deleted_at, created_at DESC, id DESC);

CREATE INDEX idx_files_created_by_user
    ON files(created_by_user_id);
