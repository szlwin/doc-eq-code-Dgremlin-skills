#!/usr/bin/env python3
"""校验评测源码清单；与独立安装 Skill 的 FILE_MANIFEST.sha256 分开维护。"""
import argparse
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MANIFEST_NAME = "EVAL_MANIFEST.sha256"


def sources():
    result = []
    for path in ROOT.rglob("*"):
        relative = path.relative_to(ROOT)
        if not path.is_file() or relative.as_posix() == MANIFEST_NAME:
            continue
        if any(part.startswith(".") or part == "__pycache__" for part in relative.parts):
            continue
        if relative.parts[0].startswith("iteration-") or relative.parts[0] in {"reports", "outputs"}:
            continue
        if path.suffix != ".pyc":
            result.append(path)
    return sorted(result)


def expected():
    return "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  ./{path.relative_to(ROOT).as_posix()}\n"
                   for path in sources())


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="刷新评测源码清单")
    args = parser.parse_args(argv)
    manifest = ROOT / MANIFEST_NAME
    content = expected()
    if args.write:
        manifest.write_text(content, encoding="utf-8")
        print(f"WROTE {manifest}")
        return 0
    if not manifest.exists() or manifest.read_text(encoding="utf-8") != content:
        print(f"FAILED: {ROOT.parent.name} eval manifest is stale; run evals/check_manifest.py --write")
        return 1
    print(f"PASSED: {ROOT.parent.name} eval manifest ({len(sources())} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
