SELECT query,
       calls,
       mean_exec_time,
       min_exec_time,
       max_exec_time,
       total_exec_time,
       rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC;
