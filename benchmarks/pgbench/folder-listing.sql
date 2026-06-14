SELECT child.id, child.name
FROM folders child
WHERE child.owner_user_id = :'owner_user_id'
  AND child.parent_folder_id IS NOT DISTINCT FROM :'folder_id'
  AND child.deleted_at IS NULL
ORDER BY child.name, child.id
LIMIT 100;
