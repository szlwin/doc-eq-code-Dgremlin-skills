#!/usr/bin/env python3
"""Run tests against fixed inputs and record fresh JUnit evidence for DEC Binding validation."""
from __future__ import annotations

import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

from binding_evidence import VERSION, DESIGN_ALGORITHM, design_digest, source_digest, yaml_digest, file_digest, junit_cases
from validate_binding import main as validate


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--design", type=Path, required=True)
    parser.add_argument("--binding", type=Path, required=True)
    parser.add_argument("--project-root", type=Path, required=True)
    parser.add_argument("--report-dir", action="append", default=[], help="project-relative JUnit directory; repeatable")
    parser.add_argument("--input", type=Path, action="append", default=[], help="additional source/dependency path, relative to project root")
    parser.add_argument("--output", type=Path, help="default: PROJECT/target/dec-binding-evidence.json")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    try:
        root, design, binding = args.project_root.resolve(), args.design.resolve(), args.binding.resolve()
        output = (args.output if args.output else root / "target/dec-binding-evidence.json").resolve()
        if (not output.is_relative_to(root) or len(output.relative_to(root).parts) < 2
                or output.relative_to(root).parts[0] not in {"target", "build"}):
            raise ValueError("evidence output must be inside a project target/build directory (normally target)")
        # A failed/preflight-aborted run must not leave an older success certificate in place.
        output.unlink(missing_ok=True)
        common = ["--design", str(design), "--binding", str(binding), "--project-root", str(root),
                  "--require-complete", "--require-tests", "--require-symbols"]
        if validate(common):
            return 1
        command = args.command[1:] if args.command[:1] == ["--"] else args.command
        if not command:
            raise ValueError("provide a test command after --; existing reports cannot be stamped retroactively")
        inputs = [(root / path).resolve() for path in args.input]
        def snapshot():
            return {"designDigest": design_digest(design), "bindingDigest": yaml_digest(binding),
                    "sourceDigest": source_digest(root), "additionalInputs": [
                        {"path": os.path.relpath(path, root), "digest": source_digest(path)} for path in inputs]}
        before = snapshot()
        start_ns = time.time_ns()
        result = subprocess.run(command, cwd=root)
        if result.returncode:
            raise ValueError(f"test command failed: exitCode={result.returncode}; no evidence recorded")
        if before != snapshot():
            raise ValueError("design, Binding, source or dependency inputs changed during the test run")
        reports = []
        for name in args.report_dir or ["target/surefire-reports"]:
            directory = (root / name).resolve()
            if not directory.is_relative_to(root):
                raise ValueError(f"report directory escapes project root: {name}")
            for report in sorted(directory.glob("*.xml")):
                if not report.resolve().is_relative_to(root):
                    raise ValueError(f"report escapes project root: {report}")
                if report.stat().st_mtime_ns < start_ns:
                    raise ValueError(f"stale test report not produced by this run: {report}; use a clean test run")
                junit_cases(report)
                reports.append({"file": report.relative_to(root).as_posix(), "digest": file_digest(report)})
        if not reports:
            raise ValueError("test command produced no fresh JUnit reports")
        evidence = {"version": VERSION, "designDigestAlgorithm": DESIGN_ALGORITHM, **before, "exitCode": 0, "command": command,
                    "startedAt": datetime.datetime.fromtimestamp(start_ns / 1e9, datetime.timezone.utc).isoformat(),
                    "finishedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "reports": reports}
        output.parent.mkdir(parents=True, exist_ok=True)
        # Exclusive creation prevents a pre-existing symlink at a predictable
        # pending filename from redirecting the write outside the project.
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent,
                                         prefix=output.stem + ".", suffix=".pending.json", delete=False) as stream:
            pending = Path(stream.name)
            stream.write(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
        try:
            if validate(common + ["--require-evidence", "--evidence", str(pending)]):
                return 1
            pending.replace(output)
        finally:
            pending.unlink(missing_ok=True)
        print(f"Evidence recorded: {output}")
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
