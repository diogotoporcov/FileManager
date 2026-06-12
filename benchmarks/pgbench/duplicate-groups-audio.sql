SELECT af.fingerprint_algorithm, af.fingerprint_version, af.fingerprint_hash, count(f.id)
FROM audio_fingerprints af
JOIN files f ON f.id = af.file_id
LEFT JOIN folders folder ON folder.id = f.folder_id
WHERE f.owner_user_id = :'owner_user_id'
  AND f.deleted_at IS NULL
  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
  AND lower(f.mime_type) LIKE 'audio/%'
GROUP BY af.fingerprint_algorithm, af.fingerprint_version, af.fingerprint_hash
HAVING count(f.id) > 1
ORDER BY count(f.id) DESC, af.fingerprint_algorithm, af.fingerprint_version
LIMIT 50;
