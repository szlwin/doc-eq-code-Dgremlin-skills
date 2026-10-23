#!/usr/bin/env python3
"""Create or verify the hash list of maintained dec-implementation skill sources."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "FILE_MANIFEST.sha256"
SOURCE_ROOTS = ("agents", "examples", "references", "schemas", "scripts", "tests")
GENERATED_DIRS = {"__pycache__", "target", "build"}


def sources() -> list[Path]:
    paths = [ROOT / name for name in ("README.md", "SKILL.md", "CHANGELOG.md", "requirements.txt")]
    for name in SOURCE_ROOTS:
        paths.extend(path for path in (ROOT / name).rglob("*") if path.is_file()
                     and not any(part in GENERATED_DIRS for part in path.relative_to(ROOT).parts))
    return sorted(path for path in paths if path.exists() and not path.name.startswith(".")
                  and path.suffix != ".pyc")


def expected() -> str:
    return "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  ./{path.relative_to(ROOT).as_posix()}\n"
                   for path in sources())


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="refresh the tracked manifest")
    args = parser.parse_args(argv)
    content = expected()
    if args.write:
        MANIFEST.write_text(content, encoding="utf-8")
        print(f"WROTE {MANIFEST}")
        return 0
    if not MANIFEST.exists() or MANIFEST.read_text(encoding="utf-8") != content:
        print("FAILED: dec-implementation source manifest is stale; run scripts/check_manifest.py --write")
        return 1
    print(f"PASSED: dec-implementation source manifest ({len(sources())} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
