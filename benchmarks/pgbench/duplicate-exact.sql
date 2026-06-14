SELECT f.id
FROM file_fingerprints fp
JOIN files f ON f.id = fp.file_id
LEFT JOIN folders folder ON folder.id = f.folder_id
WHERE fp.algorithm = 'SHA256'
  AND fp.hash_value = :'hash_value'
  AND f.owner_user_id = :'owner_user_id'
  AND f.id <> :'source_file_id'
  AND f.deleted_at IS NULL
  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
ORDER BY f.created_at DESC, f.id
LIMIT 100;
