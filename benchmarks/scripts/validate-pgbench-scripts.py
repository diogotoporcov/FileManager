import argparse
import re
from pathlib import Path


SET_PATTERN = re.compile(r"^\\set\s+([A-Za-z_][A-Za-z0-9_]*)\b", re.MULTILINE)
VARIABLE_PATTERN = re.compile(r":'?([A-Za-z_][A-Za-z0-9_]*)'?\b")

REQUIRED_VARIABLES = {
    "duplicate-audio.sql": {"fingerprint_algorithm", "fingerprint_version", "fingerprint_hash", "owner_user_id", "source_file_id"},
    "duplicate-exact.sql": {"hash_value", "owner_user_id", "source_file_id"},
    "duplicate-groups-exact.sql": {"owner_user_id"},
    "duplicate-image-embedding.sql": {"source_file_id", "model_name", "model_version", "owner_user_id", "max_distance"},
    "duplicate-image-phash.sql": {"owner_user_id", "source_file_id", "source_phash", "max_distance"},
}
REQUIRED_RESTRICTIONS = {
    "owner_user_id": "owner_user_id",
    "deleted-resource": "deleted_at IS NULL",
}


def validate_script(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    defined = set(SET_PATTERN.findall(text))
    used = set(VARIABLE_PATTERN.findall(text))
    expected = REQUIRED_VARIABLES.get(path.name)
    errors = []

    if expected is None:
        errors.append(f"{path}: no required-variable contract is defined")
        expected = set()

    missing = sorted(expected - used)
    unexpected = sorted(used - expected)
    unused = sorted(defined - used)

    errors.extend(f"{path}: required pgbench variable is missing: {name}" for name in missing)
    errors.extend(f"{path}: stale or unexpected pgbench variable is used: {name}" for name in unexpected)
    errors.extend(f"{path}: unused pgbench variable defined with \\set: {name}" for name in unused)

    restrictions = REQUIRED_RESTRICTIONS.copy()
    if path.name == "duplicate-groups-exact.sql" and "FROM exact_duplicate_groups" in text:
        restrictions["exact-summary-count"] = "active_file_count > 1"
        restrictions.pop("deleted-resource")

    for restriction, token in restrictions.items():
        if token not in text:
            errors.append(f"{path}: required restriction is missing: {restriction}")

    if "LIMIT" not in text:
        errors.append(f"{path}: required restriction is missing: limit")

    if "duplicate-image-embedding" in path.name:
        for token in ["model_name", "model_version", "dimension = 768"]:
            if token not in text:
                errors.append(f"{path}: required embedding restriction is missing: {token}")

    if "audio" in path.name:
        for token in ["fingerprint_algorithm", "fingerprint_version"]:
            if token not in text:
                errors.append(f"{path}: required audio fingerprint restriction is missing: {token}")

    if "image-phash" in path.name and "filemanager_hex_hamming_distance" not in text:
        errors.append(f"{path}: required pHash distance restriction is missing")

    return errors


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate local pgbench script variable consistency.")
    parser.add_argument("--pgbench-dir", default="benchmarks/pgbench")
    args = parser.parse_args()

    pgbench_dir = Path(args.pgbench_dir)
    if not pgbench_dir.is_dir():
        raise SystemExit(f"pgbench directory does not exist: {pgbench_dir}")

    errors = []

    for path in sorted(pgbench_dir.glob("*.sql")):
        errors.extend(validate_script(path))

    if errors:
        raise SystemExit("\n".join(errors))


if __name__ == "__main__":
    main()
