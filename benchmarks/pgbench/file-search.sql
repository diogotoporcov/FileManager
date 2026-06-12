SELECT f.id, f.name, f.mime_type, f.size
FROM files f
WHERE f.owner_user_id = :'owner_user_id'
  AND f.deleted_at IS NULL
ORDER BY f.created_at DESC, f.id DESC
LIMIT 50;
