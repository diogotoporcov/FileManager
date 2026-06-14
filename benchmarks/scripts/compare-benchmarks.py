import argparse
import csv
import json
from pathlib import Path

COMPARISON_COLUMNS = [
    "comparison_type",
    "comparison_status",
    "operation",
    "scope",
    "coldOrWarm",
    "p95_change_percent",
]


def read_summary(path: Path) -> dict[str, dict[str, str]]:
    with path.open(encoding="utf-8") as handle:
        rows = {}

        for row in csv.DictReader(handle):
            key = measurement_identity(row)

            if key in rows:
                raise SystemExit(f"Duplicate measurement identity in {path}: {key}")

            rows[key] = row

        return rows


def measurement_identity(row: dict[str, str]) -> str:
    return "|".join([row["operation"], row["scope"], row["coldOrWarm"]])


def percent_change(current: float, baseline: float) -> tuple[str, float | None]:
    if baseline == 0:
        if current == 0:
            return ("AVAILABLE", 0.0)

        return ("UNAVAILABLE_ZERO_BASELINE", None)

    return ("AVAILABLE", ((current - baseline) / baseline) * 100.0)


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare compatible local benchmark summary.csv files.")
    parser.add_argument("--baseline", required=True, help="Baseline scale directory containing environment.json and summary.csv.")
    parser.add_argument("--candidate", required=True, help="Candidate scale directory containing environment.json and summary.csv.")
    parser.add_argument("--output", required=True)

    args = parser.parse_args()

    baseline_dir = Path(args.baseline)
    current_dir = Path(args.candidate)

    rows = build_comparison_rows(baseline_dir, current_dir)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=COMPARISON_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)

    write_baseline_markdown(output.with_suffix(".md"), rows)
    validate_baseline_comparison(output)


def write_baseline_markdown(path: Path, rows: list[dict[str, object]]) -> None:
    lines = [
        "# Baseline Comparison",
        "",
        "| comparison | status | operation | scope | p95 change % |",
        "| --- | --- | --- | --- | ---: |",
    ]

    for row in rows:
        change = row["p95_change_percent"] if row["p95_change_percent"] != "" else "N/A"

        lines.append(
            f"| {row['comparison_type']} | {row['comparison_status']} | {row['operation']} | {row['scope']} | "
            f"{format_change(change)} |"
        )

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def format_change(value: object) -> str:
    if value == "N/A":
        return "N/A"
    return f"{float(value):.3f}"


def validate_baseline_comparison(csv_path: Path) -> None:
    if not csv_path.is_file():
        raise SystemExit(f"baseline comparison CSV is missing: {csv_path}")

    with csv_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)

        if reader.fieldnames != COMPARISON_COLUMNS:
            raise SystemExit(f"Invalid baseline comparison CSV schema: {csv_path}")

        rows = list(reader)

    if not rows:
        raise SystemExit(f"baseline comparison CSV must contain at least one row: {csv_path}")

    markdown = csv_path.with_suffix(".md")

    if not markdown.is_file() or markdown.stat().st_size == 0:
        raise SystemExit(f"baseline comparison Markdown is missing or empty: {markdown}")


def build_comparison_rows(baseline_dir: Path, current_dir: Path) -> list[dict[str, object]]:
    comparison_type = comparison_type_for(baseline_dir, current_dir)
    baseline = read_summary(baseline_dir / "summary.csv")
    current = read_summary(current_dir / "summary.csv")

    if comparison_type == "STRICT_REGRESSION_COMPARISON":
        require_same_measurement_identities(baseline, current)

    rows = []

    for identity, current_row in current.items():
        if identity not in baseline:
            continue

        status, change = percent_change(float(current_row["p95Ms"]), float(baseline[identity]["p95Ms"]))

        rows.append({
            "comparison_type": comparison_type,
            "comparison_status": status,
            "operation": current_row["operation"],
            "scope": current_row["scope"],
            "coldOrWarm": current_row["coldOrWarm"],
            "p95_change_percent": "" if change is None else change,
        })

    return rows


def require_same_measurement_identities(
        baseline: dict[str, dict[str, str]],
        current: dict[str, dict[str, str]]) -> None:
    baseline_keys = set(baseline)
    current_keys = set(current)

    missing = sorted(baseline_keys - current_keys)
    additional = sorted(current_keys - baseline_keys)

    if missing or additional:
        details = []

        if missing:
            details.append("missing candidate operations: " + ", ".join(missing))

        if additional:
            details.append("additional candidate operations: " + ", ".join(additional))

        raise SystemExit("Cannot compare non-equivalent benchmark results: " + "; ".join(details))


def comparison_type_for(baseline_dir: Path, current_dir: Path) -> str:
    baseline_env = json.loads((baseline_dir / "environment.json").read_text(encoding="utf-8"))
    current_env = json.loads((current_dir / "environment.json").read_text(encoding="utf-8"))

    for name, environment in {"baseline": baseline_env, "candidate": current_env}.items():
        if environment.get("schemaVersion") != 3:
            raise SystemExit(f"Unsupported environment schema for {name}: {environment.get('schemaVersion')}")

    if baseline_env.get("benchmarkProfile") != "baseline" or current_env.get("benchmarkProfile") != "baseline":
        return "INFORMATIONAL_COMPARISON"

    if baseline_env.get("baselineQuality") != "stable" or current_env.get("baselineQuality") != "stable":
        return "INFORMATIONAL_COMPARISON"

    keys = [
        "benchmarkProfile",
        "baselineQuality",
        "environmentFingerprint",
        "datasetScale",
        "recordCount",
        "datasetSeed",
        "duplicateDistribution",
        "warmupIterations",
        "measuredIterations",
        "concurrency",
        "instrumentationMode",
        "benchmarkSchemaVersion",
    ]

    for key in keys:
        if baseline_env.get(key) != current_env.get(key):
            raise SystemExit(f"Cannot compare non-equivalent benchmark results: {key} differs")

    runtime_keys = ["javaVersion", "postgresqlVersion", "pgvectorVersion"]

    for key in runtime_keys:
        if baseline_env.get("runtime", {}).get(key) != current_env.get("runtime", {}).get(key):
            raise SystemExit(f"Cannot compare non-equivalent benchmark results: runtime.{key} differs")

    return "STRICT_REGRESSION_COMPARISON"


if __name__ == "__main__":
    main()
