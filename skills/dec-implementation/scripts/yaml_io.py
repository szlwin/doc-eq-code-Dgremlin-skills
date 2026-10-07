#!/usr/bin/env python3
"""Script-only YAML I/O for DEC skills.

AI/skill workflows should not read or mutate YAML with ad-hoc file parsing or
manual text edits. Use this tool for generic YAML inspection and writes, then
run the domain validator for the artifact type.

Commands:
  read   FILE|DIR              Parse YAML and emit JSON to stdout.
  check  FILE|DIR              Parse-only syntax check.
  write  OUTPUT --from-json X  Create/replace YAML atomically from JSON.
  merge  INPUT --patch-json X  Deep-merge a JSON object and write atomically.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import sys
import tempfile
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence

import yaml


class LiteralDumper(yaml.SafeDumper):
    pass


def _str_representer(dumper: yaml.Dumper, value: str):
    style = "|" if "\n" in value else None
    return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)


LiteralDumper.add_representer(str, _str_representer)


def _yaml_files(path: Path) -> List[Path]:
    if path.is_file():
        if path.suffix.lower() not in {".yaml", ".yml"}:
            raise ValueError(f"not a YAML file: {path}")
        return [path]
    if not path.is_dir():
        raise ValueError(f"path does not exist: {path}")
    return sorted(p for p in path.rglob("*") if p.is_file() and p.suffix.lower() in {".yaml", ".yml"})


def _load(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def _json_default(value: Any) -> Any:
    if isinstance(value, (_dt.date, _dt.datetime)):
        return value.isoformat()
    raise TypeError(f"not JSON serializable: {type(value).__name__}")


def _load_json_source(spec: str) -> Any:
    if spec == "-":
        return json.load(sys.stdin)
    with Path(spec).open("r", encoding="utf-8") as f:
        return json.load(f)


def _dump_yaml(value: Any) -> str:
    return yaml.dump(
        value,
        Dumper=LiteralDumper,
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    )


def _atomic_write(path: Path, text: str) -> None:
    if path.suffix.lower() not in {".yaml", ".yml"}:
        raise ValueError(f"YAML output must end with .yaml or .yml: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


def _deep_merge(base: Any, patch: Any) -> Any:
    if isinstance(base, Mapping) and isinstance(patch, Mapping):
        out: Dict[str, Any] = dict(base)
        for key, value in patch.items():
            if key in out:
                out[key] = _deep_merge(out[key], value)
            else:
                out[key] = value
        return out
    # Lists and scalars are intentionally replaced, not merged by index.
    return patch


def cmd_read(path: Path) -> int:
    files = _yaml_files(path)
    if path.is_file():
        payload = _load(files[0])
    else:
        payload = {
            "root": str(path.resolve()),
            "files": [
                {"path": str(f.relative_to(path)), "document": _load(f)}
                for f in files
            ],
        }
    print(json.dumps(payload, ensure_ascii=False, indent=2, default=_json_default))
    return 0


def cmd_check(path: Path) -> int:
    files = _yaml_files(path)
    for f in files:
        _load(f)
    print(f"PASSED: yaml_files={len(files)}")
    return 0


def cmd_write(output: Path, source: str) -> int:
    payload = _load_json_source(source)
    _atomic_write(output, _dump_yaml(payload))
    print(str(output))
    return 0


def cmd_merge(input_path: Path, patch_source: str, output: Optional[Path]) -> int:
    if not input_path.is_file():
        raise ValueError("merge requires one YAML input file")
    base = _load(input_path)
    patch = _load_json_source(patch_source)
    if not isinstance(patch, Mapping):
        raise ValueError("merge patch must be a JSON object")
    merged = _deep_merge(base, patch)
    target = output or input_path
    _atomic_write(target, _dump_yaml(merged))
    print(str(target))
    return 0


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Script-only YAML I/O for DEC artifacts")
    sub = parser.add_subparsers(dest="command", required=True)

    p_read = sub.add_parser("read", help="parse YAML and emit JSON")
    p_read.add_argument("path", type=Path)

    p_check = sub.add_parser("check", help="parse YAML without exposing raw file reads")
    p_check.add_argument("path", type=Path)

    p_write = sub.add_parser("write", help="atomically create/replace YAML from JSON")
    p_write.add_argument("output", type=Path)
    p_write.add_argument("--from-json", required=True, help="JSON file path or '-' for stdin")

    p_merge = sub.add_parser("merge", help="deep-merge JSON object into YAML and write atomically")
    p_merge.add_argument("input", type=Path)
    p_merge.add_argument("--patch-json", required=True, help="JSON file path or '-' for stdin")
    p_merge.add_argument("-o", "--output", type=Path)

    args = parser.parse_args(argv)
    try:
        if args.command == "read":
            return cmd_read(args.path.resolve())
        if args.command == "check":
            return cmd_check(args.path.resolve())
        if args.command == "write":
            return cmd_write(args.output.resolve(), args.from_json)
        if args.command == "merge":
            return cmd_merge(args.input.resolve(), args.patch_json, args.output.resolve() if args.output else None)
        raise AssertionError(args.command)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
