SELECT f.id
FROM image_fingerprints candidate
JOIN files f ON f.id = candidate.file_id
LEFT JOIN folders folder ON folder.id = f.folder_id
WHERE f.owner_user_id = :'owner_user_id'
  AND f.id <> :'source_file_id'
  AND f.deleted_at IS NULL
  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
  AND lower(f.mime_type) LIKE 'image/%'
  AND filemanager_hex_hamming_distance(candidate.phash, :'source_phash') <= :max_distance
ORDER BY filemanager_hex_hamming_distance(candidate.phash, :'source_phash'), f.created_at DESC, f.id
LIMIT 100;
