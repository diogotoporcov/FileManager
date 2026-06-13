import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("compare-benchmarks.py")
SPEC = importlib.util.spec_from_file_location("compare_benchmarks", SCRIPT)
compare = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(compare)


class CompareBenchmarksTest(unittest.TestCase):
    def test_same_scale_equivalent_baseline_comparison_succeeds(self) -> None:
        with comparison_dirs() as (baseline, candidate):
            self.assertEqual(compare.comparison_type_for(baseline, candidate), "STRICT_REGRESSION_COMPARISON")
            self.assertEqual(len(compare.build_comparison_rows(baseline, candidate)), 1)

    def test_10k_versus_100k_is_rejected(self) -> None:
        with comparison_dirs(candidate_env={"datasetScale": "100k", "recordCount": 100_000}) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "datasetScale differs")

    def test_record_count_mismatch_is_rejected(self) -> None:
        with comparison_dirs(candidate_env={"recordCount": 100_000}) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "recordCount differs")

    def test_warmup_mismatch_is_rejected(self) -> None:
        with comparison_dirs(candidate_env={"warmupIterations": 10}) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "warmupIterations differs")

    def test_measured_iteration_mismatch_is_rejected(self) -> None:
        with comparison_dirs(candidate_env={"measuredIterations": 50}) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "measuredIterations differs")

    def test_concurrency_mismatch_is_rejected(self) -> None:
        with comparison_dirs(candidate_env={"concurrency": "1"}) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "concurrency differs")

    def test_missing_candidate_operation_is_rejected_for_strict_comparison(self) -> None:
        with comparison_dirs(candidate_rows=[]) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "missing candidate operations")

    def test_additional_candidate_operation_is_rejected_for_strict_comparison(self) -> None:
        extra = [summary_row("operation"), summary_row("additional")]
        with comparison_dirs(candidate_rows=extra) as (baseline, candidate):
            assert_rejected(self, baseline, candidate, "additional candidate operations")

    def test_default_profile_comparison_remains_informational(self) -> None:
        with comparison_dirs(
                baseline_env={"benchmarkProfile": "default", "baselineQuality": "informational"},
                candidate_env={"benchmarkProfile": "default", "baselineQuality": "informational", "recordCount": 100_000},
                candidate_rows=[summary_row("operation"), summary_row("additional")]) as (baseline, candidate):
            self.assertEqual(compare.comparison_type_for(baseline, candidate), "INFORMATIONAL_COMPARISON")
            self.assertEqual(len(compare.build_comparison_rows(baseline, candidate)), 1)

    def test_zero_baseline_and_zero_candidate_reports_zero_change(self) -> None:
        self.assertEqual(compare.percent_change(0.0, 0.0), ("AVAILABLE", 0.0))

    def test_zero_baseline_and_nonzero_candidate_reports_unavailable_change(self) -> None:
        self.assertEqual(compare.percent_change(1.0, 0.0), ("UNAVAILABLE_ZERO_BASELINE", None))

    def test_zero_baseline_rows_use_empty_percentage_value(self) -> None:
        with comparison_dirs(
                baseline_rows=[summary_row("operation", p95="0")],
                candidate_rows=[summary_row("operation", p95="1")]) as (baseline, candidate):
            row = compare.build_comparison_rows(baseline, candidate)[0]

            self.assertEqual(row["comparison_status"], "UNAVAILABLE_ZERO_BASELINE")
            self.assertEqual(row["p95_change_percent"], "")


def assert_rejected(test: unittest.TestCase, baseline: Path, candidate: Path, message: str) -> None:
    with test.assertRaises(SystemExit) as failure:
        compare.build_comparison_rows(baseline, candidate)

    test.assertIn(message, str(failure.exception))


class comparison_dirs:
    def __init__(
            self,
            baseline_env: dict[str, object] | None = None,
            candidate_env: dict[str, object] | None = None,
            baseline_rows: list[dict[str, str]] | None = None,
            candidate_rows: list[dict[str, str]] | None = None) -> None:
        self.baseline_env = baseline_env or {}
        self.candidate_env = candidate_env or {}
        self.baseline_rows = baseline_rows or [summary_row("operation")]
        self.candidate_rows = candidate_rows if candidate_rows is not None else [summary_row("operation")]

        self.temp = tempfile.TemporaryDirectory()

    def __enter__(self) -> tuple[Path, Path]:
        root = Path(self.temp.name)
        baseline = root / "baseline"
        candidate = root / "candidate"

        write_run(baseline, self.baseline_env, self.baseline_rows)
        write_run(candidate, self.candidate_env, self.candidate_rows)

        return baseline, candidate

    def __exit__(self, *args: object) -> None:
        self.temp.cleanup()


def write_run(path: Path, env_overrides: dict[str, object], rows: list[dict[str, str]]) -> None:
    path.mkdir(parents=True)

    environment = {
        "schemaVersion": 3,
        "benchmarkProfile": "baseline",
        "baselineQuality": "stable",
        "environmentFingerprint": "fingerprint",
        "datasetScale": "10k",
        "recordCount": 10_000,
        "datasetSeed": 20260611,
        "duplicateDistribution": "default",
        "warmupIterations": 20,
        "measuredIterations": 100,
        "concurrency": "1,10,25",
        "instrumentationMode": "metrics",
        "benchmarkSchemaVersion": 3,
        "runtime": {
            "javaVersion": "25",
            "postgresqlVersion": "18",
            "pgvectorVersion": "0.8.2",
        },
    }

    environment.update(env_overrides)

    (path / "environment.json").write_text(json.dumps(environment), encoding="utf-8")

    with (path / "summary.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["operation", "scope", "coldOrWarm", "p95Ms"])
        writer.writeheader()
        writer.writerows(rows)


def summary_row(operation: str, p95: str = "2.0") -> dict[str, str]:
    return {
        "operation": operation,
        "scope": "SPRING_SERVICE_REPOSITORY",
        "coldOrWarm": "WARM",
        "p95Ms": p95,
    }


if __name__ == "__main__":
    unittest.main()
