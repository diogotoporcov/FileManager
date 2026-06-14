import importlib.util
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path


SCRIPT = Path(__file__).with_name("run-benchmark-suite.py")
SPEC = importlib.util.spec_from_file_location("run_benchmark_suite", SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner)


class RunBenchmarkSuiteTest(unittest.TestCase):
    def test_default_profile_command_construction(self) -> None:
        command = runner.build_gradle_command(args(profile="default"), Path("/repo"), os_name="posix")

        self.assertTrue(command[0].endswith("gradlew"))
        self.assertIn("benchmark10k", command)
        self.assertIn("-PbenchmarkProfile=default", command)
        self.assertNotIn("-PbenchmarkProfileFile=", " ".join(command))

    def test_baseline_profile_command_construction(self) -> None:
        with tempfile.TemporaryDirectory(prefix="profile with spaces ") as temp:
            profile_file = Path(temp) / "baseline profile.yml"
            profile_file.write_text("profile: baseline\n", encoding="utf-8")

            command = runner.build_gradle_command(
                args(
                    profile="baseline",
                    profile_file=str(profile_file),
                    baseline_cpu="cpu",
                    baseline_memory="16g",
                    baseline_storage="ssd",
                    baseline_docker_resource_limits="cpus=4,memory=16g",
                    scale="100k",
                ),
                Path("C:/repo"),
                os_name="nt",
            )

            self.assertTrue(command[0].endswith("gradlew.bat"))
            self.assertIn("benchmark100k", command)
            self.assertIn(f"-PbenchmarkProfileFile={profile_file.resolve()}", command)
            self.assertIn("-PbenchmarkBaselineCpu=cpu", command)
            self.assertIn("-PbenchmarkBaselineMemory=16g", command)
            self.assertIn("-PbenchmarkBaselineStorage=ssd", command)
            self.assertIn("-PbenchmarkBaselineDockerResourceLimits=cpus=4,memory=16g", command)

    def test_baseline_missing_profile_file(self) -> None:
        with self.assertRaises(SystemExit) as failure:
            runner.build_gradle_command(args(profile="baseline", profile_file=None), Path("/repo"))

        self.assertIn("--profile-file", str(failure.exception))

    def test_baseline_missing_each_required_metadata_field(self) -> None:
        fields = {
            "baseline_cpu": "--baseline-cpu",
            "baseline_memory": "--baseline-memory",
            "baseline_storage": "--baseline-storage",
            "baseline_docker_resource_limits": "--baseline-docker-resource-limits",
        }

        for attribute, argument in fields.items():
            values = {
                "profile": "baseline",
                "profile_file": "baseline.yml",
                "baseline_cpu": "cpu",
                "baseline_memory": "16g",
                "baseline_storage": "ssd",
                "baseline_docker_resource_limits": "limits",
            }

            values[attribute] = None

            with self.assertRaises(SystemExit) as failure:
                runner.build_gradle_command(args(**values), Path("/repo"))

            self.assertIn(argument, str(failure.exception))

    def test_unix_gradle_wrapper_selection(self) -> None:
        self.assertEqual(runner.gradle_wrapper("posix"), "./gradlew")

    def test_windows_gradle_wrapper_selection(self) -> None:
        self.assertEqual(runner.gradle_wrapper("nt"), "gradlew.bat")

    def test_log_paths_are_under_benchmarks_and_assign_run_id(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            values = args(scale="1m")

            stdout_log, stderr_log = runner.benchmark_log_paths(values, root)

            self.assertEqual(root.resolve() / "benchmarks/logs" / f"benchmark-1m-{values.run_id}.log", stdout_log)
            self.assertEqual(root.resolve() / "benchmarks/logs" / f"benchmark-1m-{values.run_id}.err.log", stderr_log)
            self.assertRegex(values.run_id, r"^\d{14}-[0-9a-f]{8}$")

    def test_log_paths_reject_root_output_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(SystemExit) as failure:
                runner.benchmark_log_paths(args(logs_dir="."), Path(temp))

            self.assertIn("benchmark output directory must be under", str(failure.exception))


def args(**overrides: object) -> Namespace:
    values = {
        "scale": "10k",
        "seed": "20260611",
        "concurrency": "1,10,25",
        "warmup_iterations": None,
        "measured_iterations": None,

        "profile": "default",
        "profile_file": None,
        "baseline_cpu": None,
        "baseline_memory": None,
        "baseline_storage": None,
        "baseline_docker_resource_limits": None,

        "instrumentation_mode": "metrics",
        "run_id": None,
        "logs_dir": "benchmarks/logs",
    }

    values.update(overrides)

    return Namespace(**values)


if __name__ == "__main__":
    unittest.main()
