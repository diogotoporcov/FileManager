SELECT fp.algorithm, fp.hash_value, count(f.id)
FROM file_fingerprints fp
JOIN files f ON f.id = fp.file_id
LEFT JOIN folders folder ON folder.id = f.folder_id
WHERE f.owner_user_id = :'owner_user_id'
  AND f.deleted_at IS NULL
  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
GROUP BY fp.algorithm, fp.hash_value
HAVING count(f.id) > 1
ORDER BY count(f.id) DESC, fp.algorithm, fp.hash_value
LIMIT 50;
