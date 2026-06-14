import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate-benchmark-report.py")
SPEC = importlib.util.spec_from_file_location("generate_benchmark_report", SCRIPT)
report = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(report)

COMPARE_SCRIPT = Path(__file__).with_name("compare-benchmarks.py")
COMPARE_SPEC = importlib.util.spec_from_file_location("compare_benchmarks", COMPARE_SCRIPT)
compare = importlib.util.module_from_spec(COMPARE_SPEC)
assert COMPARE_SPEC.loader is not None
COMPARE_SPEC.loader.exec_module(compare)


class GenerateBenchmarkReportTest(unittest.TestCase):
    def test_rejects_duplicate_measurement_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            scale_dir = Path(temp)
            measurements = [
                measurement("operation", "SPRING_SERVICE_REPOSITORY", "WARM"),
                measurement("operation", "SPRING_SERVICE_REPOSITORY", "WARM"),
            ]

            with self.assertRaises(SystemExit):
                report.validate_measurement_identities(measurements, scale_dir)

    def test_writes_csv_with_escaped_fields(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            scale_dir = Path(temp)
            write_scale(scale_dir, [
                measurement('operation, quote " newline\nunicode-\u00e7', "SPRING_SERVICE_REPOSITORY", "WARM")
            ])

            rows = report.write_summary(scale_dir)

            self.assertEqual(rows[0]["operation"], 'operation, quote " newline\nunicode-\u00e7')
            csv_text = (scale_dir / "summary.csv").read_text(encoding="utf-8")
            self.assertIn('"operation, quote "" newline\nunicode-\u00e7"', csv_text)

    def test_validates_generated_scale_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            scale_dir = Path(temp)
            write_complete_scale(scale_dir, "10k", 10_000)

            report.write_report(scale_dir)

            rows = report.validate_summary_csv(scale_dir)
            self.assertEqual(len(rows), 1)
            self.assertGreater((scale_dir / "report.md").stat().st_size, 0)
            report_text = (scale_dir / "report.md").read_text(encoding="utf-8")
            self.assertIn("- File embeddings: 6", report_text)
            self.assertIn("result p95", report_text)

    def test_rejects_empty_generated_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            scale_dir = Path(temp)
            (scale_dir / "summary.csv").write_text(",".join(report.SUMMARY_COLUMNS) + "\n", encoding="utf-8")

            with self.assertRaises(SystemExit):
                report.validate_summary_csv(scale_dir)

    def test_validates_scale_comparison_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run_dir = Path(temp)
            write_complete_scale(run_dir / "10k", "10k", 10_000)
            write_complete_scale(run_dir / "100k", "100k", 100_000)

            report.write_report(run_dir / "10k")
            report.write_report(run_dir / "100k")
            report.write_scale_comparison(run_dir, ["10k", "100k"])

            rows = report.validate_csv_with_headers(run_dir / "scale-comparison.csv", report.SCALE_COMPARISON_COLUMNS)
            self.assertEqual(len(rows), 2)
            self.assertGreater((run_dir / "scale-comparison.md").stat().st_size, 0)

    def test_validates_baseline_comparison_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            baseline_dir = Path(temp) / "baseline"
            candidate_dir = Path(temp) / "candidate"
            output = Path(temp) / "baseline-comparison.csv"
            write_complete_scale(baseline_dir, "10k", 10_000, profile="baseline", baseline_quality="stable")
            write_complete_scale(candidate_dir, "10k", 10_000, profile="baseline", baseline_quality="stable")

            report.write_report(baseline_dir)
            report.write_report(candidate_dir)

            baseline = compare.read_summary(baseline_dir / "summary.csv")
            candidate = compare.read_summary(candidate_dir / "summary.csv")
            identity = next(iter(candidate))
            rows = [{
                "comparison_type": compare.comparison_type_for(baseline_dir, candidate_dir),
                "comparison_status": "AVAILABLE",
                "operation": candidate[identity]["operation"],
                "scope": candidate[identity]["scope"],
                "coldOrWarm": candidate[identity]["coldOrWarm"],
                "p95_change_percent": "0.0",
            }]

            with output.open("w", newline="", encoding="utf-8") as handle:
                import csv

                writer = csv.DictWriter(handle, fieldnames=compare.COMPARISON_COLUMNS)
                writer.writeheader()
                writer.writerows(rows)

            compare.write_baseline_markdown(output.with_suffix(".md"), rows)

            compare.validate_baseline_comparison(output)


def write_scale(scale_dir: Path, measurements: list[dict[str, object]]) -> None:
    scale_dir.mkdir(parents=True, exist_ok=True)

    (scale_dir / "repository-latency.json").write_text(
        json_text({"schemaVersion": 3, "measurements": measurements}),
        encoding="utf-8",
    )


def write_complete_scale(
        scale_dir: Path,
        scale: str,
        records: int,
        profile: str = "default",
        baseline_quality: str = "informational") -> None:
    write_scale(scale_dir, [measurement(f"operation-{scale}", "SPRING_SERVICE_REPOSITORY", "WARM")])
    (scale_dir / "environment.json").write_text(
        json_text({
            "schemaVersion": 3,
            "benchmarkRunId": "run",

            "benchmarkProfile": profile,
            "baselineQuality": baseline_quality,
            "environmentFingerprint": "fingerprint",

            "datasetScale": scale,
            "recordCount": records,
            "instrumentationMode": "metrics",
            "gitCommitSha": "commit",
            "datasetSeed": 20260611,
            "duplicateDistribution": "default",

            "warmupIterations": 20,
            "measuredIterations": 100,
            "concurrency": "1,10,25",

            "postgresqlVersion": "18",
            "pgvectorVersion": "0.8.2",
            "jdkVersion": "25",
            "benchmarkSchemaVersion": 3,

            "executionEnvironment": {
                "operatingSystem": "test-os",
                "architecture": "amd64",
            },

            "runtime": {
                "javaVersion": "25",
                "pythonVersion": "Python 3.11",
                "postgresqlVersion": "18",
                "pgvectorVersion": "0.8.2",
            },
        }),
        encoding="utf-8",
    )

    (scale_dir / "dataset-manifest.json").write_text(
        json_text({
            "schemaVersion": 3,
            "datasetId": "default-10000-20260611-test",
            "datasetMode": "inline",
            "benchmarkScaleLabel": scale,
            "recordCount": records,
            "seed": 20260611,
            "duplicateDistribution": "default",
            "configFingerprint": "fingerprint",
            "actualEvidenceTableCounts": {
                "files": records,
                "file_fingerprints": records,
                "image_fingerprints": 6,
                "file_embeddings": 6,
                "audio_fingerprints": 6,
                "exact_duplicate_groups": 2,
            },
            "embeddingModelName": "openai/clip-vit-large-patch14",
            "embeddingModelVersion": "1",
            "embeddingDimension": 768,
            "audioFingerprintAlgorithm": "chromaprint",
            "audioFingerprintVersion": "fpcalc-v1",
        }),
        encoding="utf-8",
    )

    (scale_dir / "benchmark-registry.json").write_text(
        json_text({
            "schemaVersion": 3,
            "datasetId": "default-10000-20260611-test",
            "configFingerprint": "fingerprint",
            "seed": 20260611,
            "records": records,
            "duplicateDistribution": "default",
            "operations": {
                "operation-" + scale: {
                    "sourceFileIds": ["00000000-0000-0000-0000-000000000000"],
                    "sampleSize": 1,
                    "evidenceTable": "file_fingerprints",
                },
            },
        }),
        encoding="utf-8",
    )

    (scale_dir / "correctness-results.json").write_text(
        json_text({"schemaVersion": 3, "passed": True, "caseCount": 1, "failedCount": 0}),
        encoding="utf-8",
    )

    (scale_dir / "setup-timings.json").write_text(
        json_text({"schemaVersion": 3, "timingsMs": {}}),
        encoding="utf-8",
    )

    (scale_dir / "component-status.json").write_text(
        json_text({
            "schemaVersion": 3,
            "components": {
                "serviceRepositoryBenchmark": {"status": "COMPLETED"},
                "k6": {"status": "NOT_REQUESTED"},
                "pgbench": {"status": "NOT_REQUESTED"},
                "pgStatStatements": {"status": "NOT_REQUESTED"},
                "queryPlan": {"status": "NOT_REQUESTED"},
                "resourceUsage": {"status": "NOT_REQUESTED"},
            },
        }),
        encoding="utf-8",
    )


def measurement(operation: str, scope: str, cold_or_warm: str) -> dict[str, object]:
    return {
        "schemaVersion": 3,
        "operation": operation,
        "scope": scope,
        "coldOrWarm": cold_or_warm,
        "sampleCount": 100,
        "sourceSampleCount": 100,
        "warmupCount": 20,
        "successCount": 100,
        "failureCount": 0,
        "minMs": 1.0,
        "maxMs": 2.0,
        "meanMs": 1.5,
        "p50Ms": 1.5,
        "p90Ms": 1.9,
        "p95Ms": 2.0,
        "p99Ms": None,
        "p99Status": "INSUFFICIENT_SAMPLES",
        "stddevMs": 0.1,
        "standardDeviationMethod": "population",
        "resultCountMin": 0,
        "resultCountP50": 1,
        "resultCountP95": 2,
        "resultCountMax": 3,
    }


def json_text(value: dict[str, object]) -> str:
    import json

    return json.dumps(value)


if __name__ == "__main__":
    unittest.main()
