import argparse
import subprocess
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Collect local pg_stat_statements into a CSV file.")
    parser.add_argument("--database-url", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    query = (
        "SELECT query,calls,mean_exec_time,min_exec_time,max_exec_time,total_exec_time,rows "
        "FROM pg_stat_statements ORDER BY total_exec_time DESC"
    )
    result = subprocess.run(
        ["psql", args.database_url, "--csv", "-c", query],
        check=True,
        capture_output=True,
        text=True,
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    output.write_text(result.stdout, encoding="utf-8")


if __name__ == "__main__":
    main()
