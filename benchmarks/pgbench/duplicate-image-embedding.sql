SELECT f.id
FROM file_embeddings source
JOIN file_embeddings candidate
  ON candidate.file_id <> source.file_id
  AND candidate.model_name = source.model_name
  AND candidate.model_version = source.model_version
  AND candidate.dimension = source.dimension
JOIN files f ON f.id = candidate.file_id
LEFT JOIN folders folder ON folder.id = f.folder_id
WHERE source.file_id = :'source_file_id'
  AND source.model_name = :'model_name'
  AND source.model_version = :'model_version'
  AND source.dimension = 768
  AND f.owner_user_id = :'owner_user_id'
  AND f.deleted_at IS NULL
  AND (f.folder_id IS NULL OR folder.deleted_at IS NULL)
  AND lower(f.mime_type) LIKE 'image/%'
  AND (candidate.embedding <=> source.embedding) <= :max_distance
ORDER BY (candidate.embedding <=> source.embedding), f.created_at DESC, f.id
LIMIT 100;
