SELECT f.id
FROM audio_fingerprints af
JOIN files f ON f.id = af.file_id
LEFT JOIN folders folder ON folder.id = f.folder_id
WHERE af.fingerprint_algorithm = :'fingerprint_algorithm'
  AND af.fingerprint_version = :'fingerprint_version'
  AND af.fingerprint_hash = :'fingerprint_hash'
  AND f.owner_user_id = :'owner_user_id'
  AND f.id <> :'source_file_id'
  AND f.deleted_at IS NULL
  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
  AND lower(f.mime_type) LIKE 'audio/%'
ORDER BY f.created_at DESC, f.id
LIMIT 100;
