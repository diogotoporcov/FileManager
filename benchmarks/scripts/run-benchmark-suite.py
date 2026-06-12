import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
import sys
import uuid


TASKS = {
    "10k": "benchmark10k",
    "100k": "benchmark100k",
    "1m": "benchmark1m",
    "all": "benchmarkAll",
}

BASELINE_ARGUMENTS = [
    ("profile_file", "--profile-file"),
    ("baseline_cpu", "--baseline-cpu"),
    ("baseline_memory", "--baseline-memory"),
    ("baseline_storage", "--baseline-storage"),
    ("baseline_docker_resource_limits", "--baseline-docker-resource-limits"),
]


def gradle_wrapper(os_name: str | None = None) -> str:
    return "gradlew.bat" if (os_name or os.name) == "nt" else "./gradlew"


def parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run local FileManager repository benchmarks through Gradle.")

    parser.add_argument("--scale", choices=TASKS.keys(), default="10k")
    parser.add_argument("--seed", default="20260611")
    parser.add_argument("--concurrency", default="1,10,25")
    parser.add_argument("--warmup-iterations")
    parser.add_argument("--measured-iterations")

    parser.add_argument("--profile", choices=["default", "baseline"], default="default")
    parser.add_argument("--profile-file")
    parser.add_argument("--baseline-cpu")
    parser.add_argument("--baseline-memory")
    parser.add_argument("--baseline-storage")
    parser.add_argument("--baseline-docker-resource-limits")

    parser.add_argument("--instrumentation-mode", choices=["metrics", "tracing"], default="metrics")
    parser.add_argument("--run-id")
    parser.add_argument("--logs-dir", default="benchmarks/logs")

    return parser


def validate_args(args: argparse.Namespace) -> None:
    if args.profile != "baseline":
        return

    missing = [argument for attribute, argument in BASELINE_ARGUMENTS if not getattr(args, attribute)]

    if missing:
        raise SystemExit("baseline profile requires arguments: " + ", ".join(missing))


def build_gradle_command(args: argparse.Namespace, root: Path, os_name: str | None = None) -> list[str]:
    validate_args(args)

    command = [
        str(root / gradle_wrapper(os_name)),
        TASKS[args.scale],
        f"-PbenchmarkSeed={args.seed}",
        f"-PbenchmarkConcurrency={args.concurrency}",
        f"-PbenchmarkProfile={args.profile}",
        f"-PbenchmarkInstrumentationMode={args.instrumentation_mode}",
    ]

    if args.run_id:
        command.append(f"-PbenchmarkRunId={args.run_id}")

    if args.warmup_iterations:
        command.append(f"-PbenchmarkWarmupIterations={args.warmup_iterations}")

    if args.measured_iterations:
        command.append(f"-PbenchmarkMeasuredIterations={args.measured_iterations}")

    if args.profile_file:
        command.append(f"-PbenchmarkProfileFile={Path(args.profile_file).resolve()}")

    if args.baseline_cpu:
        command.append(f"-PbenchmarkBaselineCpu={args.baseline_cpu}")

    if args.baseline_memory:
        command.append(f"-PbenchmarkBaselineMemory={args.baseline_memory}")

    if args.baseline_storage:
        command.append(f"-PbenchmarkBaselineStorage={args.baseline_storage}")

    if args.baseline_docker_resource_limits:
        command.append(f"-PbenchmarkBaselineDockerResourceLimits={args.baseline_docker_resource_limits}")

    return command


def generated_run_id() -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    return f"{timestamp}-{uuid.uuid4().hex[:8]}"


def resolve_benchmark_output_dir(root: Path, configured_dir: str) -> Path:
    root = root.resolve()
    benchmarks_dir = (root / "benchmarks").resolve()
    output_dir = Path(configured_dir)

    if not output_dir.is_absolute():
        output_dir = root / output_dir

    output_dir = output_dir.resolve()

    try:
        output_dir.relative_to(benchmarks_dir)
    except ValueError:
        raise SystemExit(f"benchmark output directory must be under {benchmarks_dir}: {output_dir}")

    return output_dir


def benchmark_log_paths(args: argparse.Namespace, root: Path) -> tuple[Path, Path]:
    logs_dir = resolve_benchmark_output_dir(root, args.logs_dir)
    run_id = args.run_id or generated_run_id()
    args.run_id = run_id
    base_name = f"benchmark-{args.scale}-{run_id}"

    return logs_dir / f"{base_name}.log", logs_dir / f"{base_name}.err.log"


def run_gradle_command(command: list[str], root: Path, stdout_log: Path, stderr_log: Path) -> None:
    stdout_log.parent.mkdir(parents=True, exist_ok=True)
    stderr_log.parent.mkdir(parents=True, exist_ok=True)

    with stdout_log.open("w", encoding="utf-8") as stdout, stderr_log.open("w", encoding="utf-8") as stderr:
        subprocess.run(command, cwd=root, check=True, stdout=stdout, stderr=stderr)


def main() -> None:
    args = parser().parse_args()
    root = Path(__file__).resolve().parents[2]
    stdout_log, stderr_log = benchmark_log_paths(args, root)

    command = build_gradle_command(args, root)

    print(f"Benchmark run id: {args.run_id}")
    print(f"Gradle stdout log: {stdout_log.relative_to(root)}")
    print(f"Gradle stderr log: {stderr_log.relative_to(root)}")

    run_gradle_command(command, root, stdout_log, stderr_log)


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)
