DROP INDEX IF EXISTS ux_folders_owner_user_active_sibling_name;

CREATE UNIQUE INDEX ux_folders_owner_user_active_root_name
    ON folders(owner_user_id, lower(name))
    WHERE parent_folder_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX ux_folders_parent_active_child_name
    ON folders(parent_folder_id, lower(name))
    WHERE parent_folder_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE file_grants
    ADD CONSTRAINT chk_file_grants_permission
    CHECK (permission IN ('FILE_VIEW', 'FILE_MODIFY', 'FILE_DELETE'));

ALTER TABLE file_grants
    ADD CONSTRAINT file_grants_no_self_grant
    CHECK (grantee_user_id <> created_by_user_id);

ALTER TABLE folder_grants
    ADD CONSTRAINT chk_folder_grants_permission
    CHECK (permission IN ('FOLDER_VIEW', 'FOLDER_CREATE', 'FOLDER_RENAME', 'FOLDER_DELETE', 'FOLDER_UPLOAD_FILE'));

ALTER TABLE folder_grants
    ADD CONSTRAINT folder_grants_no_self_grant
    CHECK (grantee_user_id <> created_by_user_id);
