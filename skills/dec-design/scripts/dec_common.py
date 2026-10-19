"""Shared DEC design-script syntax and atomic YAML output helpers."""
from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path
from typing import Any, List, Mapping, Tuple

import yaml


INFO_REF_RE = re.compile(r"(?<![#A-Za-z0-9_.-])([A-Za-z_][A-Za-z0-9_-]*\.[A-Za-z_][A-Za-z0-9_-]*)(?![A-Za-z0-9_.-])")


def named_entries(value: Any) -> List[Tuple[str, Any]]:
    """Canonical named mappings and named-list entries share one traversal."""
    if isinstance(value, Mapping):
        return [(str(k), v) for k, v in value.items()]
    items = value if isinstance(value, list) else ([] if value is None else [value])
    return [(str(v["name"]), v) for v in items if isinstance(v, Mapping) and v.get("name")]


def data_property_enums(datas: Mapping[str, Mapping[str, Any]], data_name: str, property_name: str) -> set[str]:
    tables = datas.get(data_name, {}).get("tables") or []
    if not isinstance(tables, list):
        tables = [tables]
    return {str(column["relEnum"]) for table in tables if isinstance(table, Mapping)
            for _, column in named_entries(table.get("columns"))
            if isinstance(column, Mapping) and column.get("relEnum")
            and str(column.get("ref", column.get("refProperty"))) == str(property_name)}


def resolve_view_property_enums(view: Mapping[str, Any], path: str,
                                datas: Mapping[str, Mapping[str, Any]]) -> set[str]:
    """Resolve a View path through relation Data and mapped columns to relEnum.

    Both validation and rendering pass plain Data definitions, independent of
    their source-location/catalog wrappers. Ambiguity remains a set for callers.
    """
    parts = str(path).split(".")
    if not all(parts):
        return set()
    current_data = str(view.get("targetMain") or "")
    props = view.get("properties") or {}
    for index, part in enumerate(parts):
        raw = dict(named_entries(props)).get(part)
        if raw is None:
            return set()
        last = index == len(parts) - 1
        if isinstance(raw, Mapping) and raw.get("relation"):
            if last:
                return set()
            current_data = str(raw.get("data") or "")
            props = raw.get("properties") or {}
            continue
        if not last:
            return set()
        ref = raw.get("ref", raw.get("refProperty")) if isinstance(raw, Mapping) else raw
        if ref in (None, ""):
            return set()
        return data_property_enums(datas, current_data, str(ref))
    return set()


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
