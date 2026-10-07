"""Shared DEC design-script syntax and atomic YAML output helpers."""
from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path
from typing import Any, List, Mapping, Tuple

import yaml


INFO_REF_RE = re.compile(r"(?<![#A-Za-z0-9_.-])([A-Za-z_][A-Za-z0-9_-]*\.[A-Za-z_][A-Za-z0-9_-]*)(?![A-Za-z0-9_.-])")


def extract_info_refs(expression: str) -> set[str]:
    return set(INFO_REF_RE.findall(expression or ""))


def parse_change_assignments(change_data: str) -> List[Tuple[str, Any]]:
    """Read simple assignments for cross-file checks and HTML presentation."""
    text = str(change_data or "")
    out: List[Tuple[str, Any]] = []

    def scalar(raw: str) -> Any:
        value = raw.strip().strip('"\'')
        if value.lower() == "null": return None
        if value.lower() == "true": return True
        if value.lower() == "false": return False
        if re.fullmatch(r"[-+]?\d+", value): return int(value)
        if re.fullmatch(r"[-+]?(?:\d+\.\d*|\d*\.\d+)", value): return float(value)
        return value

    consumed: List[Tuple[int, int]] = []
    for match in re.finditer(r"every\(\s*([A-Za-z_][\w-]*)\s*,\s*([A-Za-z_][\w-]*)\s*:\s*([^;)]+)\)", text):
        out.append((f"{match.group(1)}.{match.group(2)}", scalar(match.group(3))))
        consumed.append((match.start(), match.end()))
    masked = list(text)
    for start, end in consumed:
        masked[start:end] = " " * (end - start)
    for match in re.finditer(r"(?:^|;)\s*([A-Za-z_][\w-]*)\s*:\s*([^;]+)", "".join(masked), flags=re.MULTILINE):
        out.append((match.group(1), scalar(match.group(2))))
    return out


class LiteralDumper(yaml.SafeDumper):
    pass


def _represent_string(dumper: yaml.Dumper, value: str):
    style = "|" if "\n" in value else None
    return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)


LiteralDumper.add_representer(str, _represent_string)


def dump_yaml(value: Any) -> str:
    return yaml.dump(value, Dumper=LiteralDumper, allow_unicode=True,
                     sort_keys=False, default_flow_style=False, width=120)


def atomic_write_yaml(path: Path, content: str) -> None:
    if path.suffix.lower() not in {".yaml", ".yml"}:
        raise ValueError(f"YAML output must end with .yaml or .yml: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)
