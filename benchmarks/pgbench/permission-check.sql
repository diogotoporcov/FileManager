SELECT EXISTS (
  SELECT 1
  FROM files f
  WHERE f.id = :'file_id'
    AND f.owner_user_id = :'owner_user_id'
    AND f.deleted_at IS NULL
);
