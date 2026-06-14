import argparse
import csv
import json
from pathlib import Path
from typing import Any


SCALES = ["10k", "100k", "1m"]
SUPPORTED_SCHEMA_VERSION = 3
REQUIRED_SCALE_ARTIFACTS = [
    "environment.json",
    "dataset-manifest.json",
    "benchmark-registry.json",
    "correctness-results.json",
    "setup-timings.json",
    "repository-latency.json",
    "component-status.json",
]
SUMMARY_COLUMNS = [
    "operation",
    "scope",
    "coldOrWarm",
    "sampleCount",
    "sourceSampleCount",
    "warmupCount",
    "successCount",
    "failureCount",
    "minMs",
    "maxMs",
    "meanMs",
    "p50Ms",
    "p90Ms",
    "p95Ms",
    "p99Ms",
    "p99Status",
    "stddevMs",
    "standardDeviationMethod",
    "resultCountMin",
    "resultCountP50",
    "resultCountP95",
    "resultCountMax",
]
SCALE_COMPARISON_COLUMNS = ["scale", "records", *SUMMARY_COLUMNS]
VALID_COMPONENT_STATUSES = {
    "COMPLETED",
    "NOT_REQUESTED",
    "NOT_CONFIGURED",
    "TOOL_UNAVAILABLE",
    "TARGET_UNAVAILABLE",
    "FAILED",
}
ALLOWED_OPERATIONS = {
    "duplicate.search.EXACT",
    "duplicate.search.IMAGE_PHASH",
    "duplicate.search.IMAGE_EMBEDDING",
    "duplicate.search.AUDIO_FINGERPRINT",
    "duplicate.groups.EXACT",
}
REMOVED_DIMENSIONS = {
    "video_embeddings",
    "duplicate_candidates",
    "duplicate_candidate_refreshes",
    "file_grants",
    "folder_grants",
    "processing_jobs",
    "VIDEO_" + "EMBEDDING",
    "candidate-" + "refresh",
    "permission." + "evaluate",
    "processing." + "status",
    "sharing." + "list",
    "folder." + "list",
    "file." + "search",
    "download-" + "control-plane",
}


def read_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON artifact: {path}: {exc}") from exc

    if data.get("schemaVersion") != SUPPORTED_SCHEMA_VERSION:
        raise SystemExit(f"Unsupported or missing schemaVersion in {path}")

    return data


def require_dict(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise SystemExit(f"Expected object for {name}")
    return value


def require_list(value: Any, name: str) -> list[Any]:
    if not isinstance(value, list):
        raise SystemExit(f"Expected array for {name}")
    return value


def validate_scale_dir(scale_dir: Path) -> None:
    missing = [name for name in REQUIRED_SCALE_ARTIFACTS if not (scale_dir / name).is_file()]

    if missing:
        raise SystemExit(f"Missing required artifacts for {scale_dir}: {', '.join(missing)}")

    correctness = read_json(scale_dir / "correctness-results.json")

    if correctness.get("passed") is not True:
        raise SystemExit(f"Correctness failed for {scale_dir}")

    component_status = read_json(scale_dir / "component-status.json")
    components = require_dict(component_status.get("components"), "component-status.json.components")
    reject_removed_dimensions(read_json(scale_dir / "dataset-manifest.json"), "dataset-manifest.json")
    reject_removed_dimensions(read_json(scale_dir / "benchmark-registry.json"), "benchmark-registry.json")

    validate_component_statuses(components, scale_dir)

    latency = read_json(scale_dir / "repository-latency.json")

    validate_measurement_identities(require_list(latency.get("measurements"), "repository-latency.json.measurements"), scale_dir)

    if components.get("pgbench", {}).get("status") == "COMPLETED" and not (scale_dir / "pgbench-summary.json").is_file():
        raise SystemExit(f"pgbench completed but pgbench-summary.json is missing for {scale_dir}")


def validate_component_statuses(components: dict[str, Any], scale_dir: Path) -> None:
    for component in ["serviceRepositoryBenchmark", "pgbench", "pgStatStatements", "queryPlan", "resourceUsage"]:
        status = require_dict(components.get(component), f"components.{component}").get("status")

        if status not in VALID_COMPONENT_STATUSES:
            raise SystemExit(f"Invalid component status for {component} in {scale_dir}: {status}")

    validate_optional_artifact(components, "pgStatStatements", scale_dir / "pg-stat-statements.csv")
    validate_optional_artifact(components, "queryPlan", scale_dir / "query-plan-manifest.json")
    validate_optional_artifact(components, "resourceUsage", scale_dir / "jvm-resource-usage.json")


def validate_optional_artifact(components: dict[str, Any], component: str, artifact: Path) -> None:
    status = require_dict(components.get(component), f"components.{component}").get("status")

    if status == "COMPLETED" and not artifact.is_file():
        raise SystemExit(f"{component} completed but artifact is missing: {artifact}")

    if status != "COMPLETED" and artifact.exists():
        raise SystemExit(f"{component} did not complete but artifact exists: {artifact}")


def write_summary(scale_dir: Path) -> list[dict[str, str]]:
    latency = read_json(scale_dir / "repository-latency.json")
    measurements = require_list(latency.get("measurements"), "repository-latency.json.measurements")

    rows = [summary_row(measurement) for measurement in measurements]
    rows.sort(key=measurement_identity)

    identities = set()

    for row in rows:
        identity = measurement_identity(row)

        if identity in identities:
            raise SystemExit(f"Duplicate measurement identity in {scale_dir}: {identity}")

        identities.add(identity)

    with (scale_dir / "summary.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=SUMMARY_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)

    return rows


def validate_summary_csv(scale_dir: Path) -> list[dict[str, str]]:
    summary = scale_dir / "summary.csv"

    if not summary.is_file():
        raise SystemExit(f"summary.csv is missing for {scale_dir}")

    with summary.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)

        if reader.fieldnames != SUMMARY_COLUMNS:
            raise SystemExit(f"Invalid summary.csv schema for {scale_dir}")

        rows = list(reader)

    if not rows:
        raise SystemExit(f"summary.csv must contain at least one row for {scale_dir}")

    identities = set()

    for row in rows:
        identity = measurement_identity(row)

        if identity in identities:
            raise SystemExit(f"Duplicate measurement identity in {scale_dir}: {identity}")

        identities.add(identity)

    return rows


def read_or_write_summary(scale_dir: Path) -> list[dict[str, str]]:
    summary = scale_dir / "summary.csv"

    if not summary.is_file():
        return write_summary(scale_dir)

    return validate_summary_csv(scale_dir)


def require_non_empty_file(path: Path, description: str) -> None:
    if not path.is_file():
        raise SystemExit(f"{description} is missing: {path}")

    if path.stat().st_size == 0:
        raise SystemExit(f"{description} is empty: {path}")


def validate_scale_report(scale_dir: Path) -> None:
    validate_summary_csv(scale_dir)
    require_non_empty_file(scale_dir / "report.md", "report.md")


def validate_scale_comparison(run_dir: Path) -> None:
    rows = validate_csv_with_headers(run_dir / "scale-comparison.csv", SCALE_COMPARISON_COLUMNS)

    if not rows:
        raise SystemExit(f"scale-comparison.csv must contain at least one row for {run_dir}")

    require_non_empty_file(run_dir / "scale-comparison.md", "scale-comparison.md")


def validate_csv_with_headers(path: Path, expected_headers: list[str]) -> list[dict[str, str]]:
    if not path.is_file():
        raise SystemExit(f"CSV artifact is missing: {path}")

    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)

        if reader.fieldnames != expected_headers:
            raise SystemExit(f"Invalid CSV schema for {path}")

        return list(reader)


def summary_row(measurement: dict[str, Any]) -> dict[str, str]:
    row = {}

    for column in SUMMARY_COLUMNS:
        if column not in measurement:
            raise SystemExit(f"Missing measurement field: {column}")

        value = measurement[column]
        row[column] = "" if value is None else str(value)

    return row


def validate_measurement_identities(measurements: list[dict[str, Any]], scale_dir: Path) -> None:
    if not measurements:
        raise SystemExit(f"repository-latency.json has no measurements for {scale_dir}")

    identities = set()

    for measurement in measurements:
        identity = measurement_identity(measurement)
        operation = str(measurement.get("operation"))

        if operation not in ALLOWED_OPERATIONS:
            raise SystemExit(f"Unsupported benchmark operation in {scale_dir}: {operation}")

        if identity in identities:
            raise SystemExit(f"Duplicate measurement identity in repository-latency.json for {scale_dir}: {identity}")

        identities.add(identity)


def measurement_identity(row: dict[str, Any]) -> tuple[str, str, str]:
    return (str(row.get("operation")), str(row.get("scope")), str(row.get("coldOrWarm")))


def write_report(scale_dir: Path) -> None:
    validate_scale_dir(scale_dir)

    environment = read_json(scale_dir / "environment.json")
    dataset = read_json(scale_dir / "dataset-manifest.json")
    registry = read_json(scale_dir / "benchmark-registry.json")
    correctness = read_json(scale_dir / "correctness-results.json")
    component_status = read_json(scale_dir / "component-status.json")

    rows = write_summary(scale_dir)
    reject_removed_dimensions(dataset, "dataset-manifest.json")
    reject_removed_dimensions(registry, "benchmark-registry.json")

    lines = [
        "# Benchmark Report",
        "",
        "## Environment",
        "",
        f"- Run ID: {environment['benchmarkRunId']}",
        f"- Benchmark profile: {environment['benchmarkProfile']}",
        f"- Baseline quality: {environment['baselineQuality']}",
        f"- Environment fingerprint: {environment['environmentFingerprint']}",
        f"- Instrumentation mode: {environment['instrumentationMode']}",
        "",
        "## Execution Environment",
        "",
        f"- Operating system: {environment['executionEnvironment'].get('operatingSystem')}",
        f"- Architecture: {environment['executionEnvironment'].get('architecture')}",
        f"- Java: {environment['runtime'].get('javaVersion')}",
        f"- Python: {environment['runtime'].get('pythonVersion')}",
        f"- PostgreSQL: {environment['runtime'].get('postgresqlVersion')}",
        "",
        "## Dataset",
        "",
        f"- Dataset ID: {dataset['datasetId']}",
        f"- Dataset mode: {dataset['datasetMode']}",
        f"- Scale: {dataset['benchmarkScaleLabel']}",
        f"- Duplicate distribution: {dataset['duplicateDistribution']}",
        f"- Records: {dataset['recordCount']} synthetic file metadata and fingerprint records",
        f"- Seed: {dataset['seed']}",
        f"- Files: {dataset['actualEvidenceTableCounts']['files']}",
        f"- File fingerprints: {dataset['actualEvidenceTableCounts']['file_fingerprints']}",
        f"- Image fingerprints: {dataset['actualEvidenceTableCounts']['image_fingerprints']}",
        f"- File embeddings: {dataset['actualEvidenceTableCounts']['file_embeddings']}",
        f"- Audio fingerprints: {dataset['actualEvidenceTableCounts']['audio_fingerprints']}",
        f"- Exact duplicate groups: {dataset['actualEvidenceTableCounts']['exact_duplicate_groups']}",
        f"- Embedding model: {dataset['embeddingModelName']}:{dataset['embeddingModelVersion']} "
        f"({dataset['embeddingDimension']} dimensions)",
        f"- Audio fingerprint: {dataset['audioFingerprintAlgorithm']}:{dataset['audioFingerprintVersion']}",
        "",
        "## Benchmark Registry",
        "",
        f"- Dataset ID: {registry['datasetId']}",
        f"- Config fingerprint: {registry['configFingerprint']}",
        "",
        "## Correctness Validation",
        "",
        f"- Passed: {correctness['passed']}",
        f"- Cases: {correctness['caseCount']}",
        f"- Failed: {correctness['failedCount']}",
        "",
        "## Component Status",
        "",
    ]

    for name, status in sorted(component_status["components"].items()):
        reason = status.get("reason")
        suffix = f" - {reason}" if reason else ""

        lines.append(f"- {name}: {status['status']}{suffix}")

    lines.extend([
        "",
        "## Spring Service/Repository Latency",
        "",
        "| operation | scope | sources | p50 ms | p95 ms | p99 ms | result p50 | result p95 | result max |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ])

    for row in sorted(rows, key=lambda item: measurement_identity(item)):
        p99 = row["p99Ms"] if row["p99Ms"] else row["p99Status"]

        lines.append(
            f"| {row['operation']} | {row['scope']} | {row['sourceSampleCount']} | {row['p50Ms']} | "
            f"{row['p95Ms']} | {p99} | {row['resultCountP50']} | {row['resultCountP95']} | "
            f"{row['resultCountMax']} |"
        )

    lines.extend([
        "",
        "## Limitations",
        "",
        "- These timings are Spring service/repository measurements, not HTTP API or gateway latency.",
        "- Synthetic records are file metadata and fingerprint records, not physical uploaded files.",
    ])

    (scale_dir / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    validate_scale_report(scale_dir)


def compatible_environments(run_dir: Path, scale_dirs: list[Path]) -> bool:
    environments = [read_json(scale_dir / "environment.json") for scale_dir in scale_dirs]

    keys = [
        "benchmarkRunId",
        "gitCommitSha",
        "datasetSeed",
        "duplicateDistribution",
        "instrumentationMode",
        "benchmarkProfile",
        "baselineQuality",
        "environmentFingerprint",
        "postgresqlVersion",
        "pgvectorVersion",
        "jdkVersion",
        "benchmarkSchemaVersion",
    ]

    first = flattened_environment(environments[0])
    compatible = all(all(flattened_environment(env).get(key) == first.get(key) for key in keys) for env in environments[1:])

    if not compatible:
        (run_dir / "scale-comparison.md").write_text(
            "# Scale Comparison\n\nNON_EQUIVALENT_COMPARISON\n",
            encoding="utf-8",
        )

    return compatible


def reject_removed_dimensions(value: Any, artifact: str) -> None:
    serialized = json.dumps(value)

    for removed in REMOVED_DIMENSIONS:
        if removed in serialized:
            raise SystemExit(f"Removed benchmark dimension in {artifact}: {removed}")


def write_scale_comparison(run_dir: Path, requested_scales: list[str]) -> None:
    scale_dirs = [run_dir / scale for scale in requested_scales]

    for scale_dir in scale_dirs:
        validate_scale_dir(scale_dir)

    if not compatible_environments(run_dir, scale_dirs):
        raise SystemExit(f"Cannot compare non-equivalent scale results in {run_dir}")

    rows = []

    for scale in requested_scales:
        scale_dir = run_dir / scale
        records = read_json(scale_dir / "dataset-manifest.json")["recordCount"]

        for row in read_or_write_summary(scale_dir):
            rows.append({"scale": scale, "records": records, **row})

    rows.sort(key=lambda item: (*measurement_identity(item), int(item["records"])))

    csv_path = run_dir / "scale-comparison.csv"

    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=SCALE_COMPARISON_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)

    lines = [
        "# Scale Comparison",
        "",
        "| operation | scope | scale | records | p50 ms | p95 ms | p99 ms |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: |",
    ]

    for row in rows:
        p99 = row["p99Ms"] if row["p99Ms"] else row["p99Status"]

        lines.append(
            f"| {row['operation']} | {row['scope']} | {row['scale']} | {row['records']} | "
            f"{row['p50Ms']} | {row['p95Ms']} | {p99} |"
        )

    (run_dir / "scale-comparison.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    validate_scale_comparison(run_dir)


def flattened_environment(environment: dict[str, Any]) -> dict[str, Any]:
    runtime = environment.get("runtime", {})

    return {
        "benchmarkRunId": environment.get("benchmarkRunId"),
        "gitCommitSha": environment.get("gitCommitSha"),
        "datasetSeed": environment.get("datasetSeed"),
        "duplicateDistribution": environment.get("duplicateDistribution"),
        "instrumentationMode": environment.get("instrumentationMode"),
        "benchmarkProfile": environment.get("benchmarkProfile"),
        "baselineQuality": environment.get("baselineQuality"),
        "environmentFingerprint": environment.get("environmentFingerprint"),
        "postgresqlVersion": runtime.get("postgresqlVersion"),
        "pgvectorVersion": runtime.get("pgvectorVersion"),
        "jdkVersion": runtime.get("javaVersion"),
        "benchmarkSchemaVersion": environment.get("benchmarkSchemaVersion"),
    }


def latest_run_id(reports_dir: Path) -> str:
    runs = [path for path in reports_dir.iterdir() if path.is_dir()]

    if not runs:
        raise SystemExit(f"No benchmark run directories exist under {reports_dir}")

    return max(runs, key=lambda path: path.stat().st_mtime).name


def main() -> None:
    parser = argparse.ArgumentParser(description="Strictly generate local benchmark reports from raw artifacts.")
    parser.add_argument("--reports-dir", default="benchmarks/reports")
    parser.add_argument("--run-id")
    parser.add_argument("--scales")
    parser.add_argument("--allow-partial", action="store_true")

    args = parser.parse_args()

    reports_dir = Path(args.reports_dir)
    run_id = args.run_id or latest_run_id(reports_dir)
    run_dir = reports_dir / run_id

    if not run_dir.is_dir():
        raise SystemExit(f"Benchmark run directory does not exist: {run_dir}")

    requested_scales = [scale.strip() for scale in args.scales.split(",") if scale.strip()] if args.scales else [
        scale for scale in SCALES if (run_dir / scale).is_dir()
    ]

    if not requested_scales:
        raise SystemExit(f"No benchmark scale directories exist under {run_dir}")

    unknown = [scale for scale in requested_scales if scale not in SCALES]

    if unknown:
        raise SystemExit(f"Unsupported scale labels: {', '.join(unknown)}")

    existing_scales = []

    for scale in requested_scales:
        scale_dir = run_dir / scale

        if scale_dir.is_dir():
            write_report(scale_dir)
            existing_scales.append(scale)
        elif not args.allow_partial:
            raise SystemExit(f"Requested scale is missing: {scale_dir}")

    if len(existing_scales) > 1:
        write_scale_comparison(run_dir, existing_scales)


if __name__ == "__main__":
    main()
