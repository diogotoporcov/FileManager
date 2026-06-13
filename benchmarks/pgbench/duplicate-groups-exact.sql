SELECT algorithm, hash_value, active_file_count
FROM exact_duplicate_groups
WHERE owner_user_id = :'owner_user_id'
  AND active_file_count > 1
ORDER BY active_file_count DESC, algorithm, hash_value
LIMIT 50;
