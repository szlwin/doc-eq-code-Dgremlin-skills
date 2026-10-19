#!/usr/bin/env python3
"""Normative validation for DEC canonical YAML.

Validation is intentionally fail-closed when the necessary facts are loaded.
When a referenced fact lives outside the supplied input set, the validator emits
an explicit warning instead of guessing.
"""
from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Set, Tuple


from dec_common import named_entries, resolve_view_property_enums

RULE_TYPES = {
    "check", "checkPattern", "checkData", "checkDataPattern",
    "insert", "update", "delete", "get", "query", "dsl", "grammer",
}
RELATIONS = {"one-to-one", "one-to-many"}
KEY_TYPES = {"increment", "set"}
API_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
API_PARAM_LOCATIONS = {"path", "query", "header", "body"}
API_VALIDATION_TYPES = {"notNull", "notEmpty", "min", "max", "minLength", "maxLength", "pattern", "regex", "enum", "expression"}
DESIGN_ID_RE = re.compile(r"^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+$")
URL_PATH_PARAM_RE = re.compile(r"\{([A-Za-z_][\w-]*)\}")
API_REF_RE = re.compile(r"^[A-Za-z_][\w-]*\.[A-Za-z_][\w-]*$")


def yaml_files(path: Path) -> List[Path]:
    if path.is_file():
        return [path]
    return sorted(p for p in path.rglob("*") if p.is_file() and p.suffix.lower() in {".yaml", ".yml"})


def walk(value: Any, path: str = "root") -> Iterable[Tuple[str, Mapping[str, Any]]]:
    if isinstance(value, Mapping):
        yield path, value
        for k, v in value.items():
            yield from walk(v, f"{path}.{k}")
    elif isinstance(value, list):
        for i, v in enumerate(value):
            yield from walk(v, f"{path}[{i}]")


def as_list(value: Any) -> List[Any]:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def err(errors: List[Dict[str, str]], file: Path | str, message: str) -> None:
    errors.append({"file": str(file), "message": message})


def warn(warnings: List[Dict[str, str]], file: Path | str, message: str) -> None:
    warnings.append({"file": str(file), "message": message})


def require_map(value: Any) -> bool:
    return isinstance(value, Mapping)



def extract_property_refs(properties: Any, prefix: str = "") -> Set[str]:
    """Return business property paths (not underlying data refs)."""
    result: Set[str] = set()
    for name, raw in named_entries(properties or {}):
        path = f"{prefix}.{name}" if prefix else name
        result.add(path)
        if isinstance(raw, Mapping) and raw.get("relation"):
            result.update(extract_property_refs(raw.get("properties", {}), path))
    return result


def resolve_view_property_enum(view, path, data_defs):
    return resolve_view_property_enums(view, path, {name: value[1] for name, value in data_defs.items()})


def validate_rule(rule: Mapping[str, Any], where: str, file: Path, errors: List[Dict[str, str]], warnings: List[Dict[str, str]]) -> None:
    name = rule.get("name")
    rtype = rule.get("type")
    if not name:
        err(errors, file, f"{where}.name: required")
    if not rtype:
        err(errors, file, f"{where}.type: required")
        return
    if rtype not in RULE_TYPES:
        err(errors, file, f"{where}.type: unsupported rule type {rtype!r}")
        return
    if rtype == "grammer":
        warn(warnings, file, f"{where}.type: legacy 'grammer' accepted; canonical YAML should use 'dsl'")
    prop = rule.get("property")
    pattern = rule.get("pattern")
    cmd = rule.get("cmd", rule.get("sql"))
    if rule.get("cmd") is not None and rule.get("sql") is not None and rule.get("cmd") != rule.get("sql"):
        err(errors, file, f"{where}: cmd and sql conflict")
    if rule.get("sql") is not None:
        warn(warnings, file, f"{where}.sql: legacy YAML field accepted; canonical YAML should use cmd")

    if rtype == "check":
        if not prop: err(errors, file, f"{where}.property: required for check")
        if not pattern: err(errors, file, f"{where}.pattern: required for check")
    elif rtype == "checkPattern":
        if not pattern: err(errors, file, f"{where}.pattern: required for checkPattern")
    elif rtype == "checkData":
        if not prop: err(errors, file, f"{where}.property: required for checkData")
        if not pattern: err(errors, file, f"{where}.pattern: required for checkData")
    elif rtype == "checkDataPattern":
        if not prop: err(errors, file, f"{where}.property: required for checkDataPattern")
        if not pattern: err(errors, file, f"{where}.pattern: required for checkDataPattern")
    elif rtype in {"insert", "update"}:
        if not prop:
            err(errors, file, f"{where}.property: required for {rtype} in canonical YAML")
    elif rtype == "delete":
        if not prop and not cmd:
            err(errors, file, f"{where}: delete requires property or cmd")
    elif rtype in {"get", "query"}:
        if not prop:
            err(errors, file, f"{where}.property: required for {rtype}")
    elif rtype in {"dsl", "grammer"}:
        if not rule.get("process") and not rule.get("grammer"):
            err(errors, file, f"{where}.process: required for dsl")

    error_info = rule.get("error")
    if error_info is not None:
        if not isinstance(error_info, Mapping):
            err(errors, file, f"{where}.error: expected mapping")
        else:
            if not error_info.get("code"): err(errors, file, f"{where}.error.code: required")
            if not error_info.get("message"): err(errors, file, f"{where}.error.message: required")



def validate_api_validations(values: Any, where: str, file: Path, errors: List[Dict[str, str]]) -> None:
    for vi, val in enumerate(as_list(values)):
        loc = f"{where}[{vi}]"
        if not isinstance(val, Mapping):
            err(errors, file, f"{loc}: expected mapping")
            continue
        vtype = val.get("type")
        if vtype not in API_VALIDATION_TYPES:
            err(errors, file, f"{loc}.type: must be one of {sorted(API_VALIDATION_TYPES)}")
            continue
        if vtype in {"min", "max", "minLength", "maxLength", "pattern", "regex"} and val.get("value") is None:
            err(errors, file, f"{loc}.value: required for {vtype}")
        value = val.get("value")
        if vtype in {"min", "max"} and value is not None and (isinstance(value, bool) or not isinstance(value, (int, float))):
            err(errors, file, f"{loc}.value: {vtype} requires a numeric value")
        if vtype in {"minLength", "maxLength"} and value is not None and (isinstance(value, bool) or not isinstance(value, int) or value < 0):
            err(errors, file, f"{loc}.value: {vtype} requires a non-negative integer")
        if vtype in {"pattern", "regex"} and value is not None and (not isinstance(value, str) or not value):
            err(errors, file, f"{loc}.value: {vtype} requires a non-empty string")
        if vtype == "enum" and not as_list(val.get("values")):
            err(errors, file, f"{loc}.values: required for enum")
        if vtype == "expression" and not val.get("expression"):
            err(errors, file, f"{loc}.expression: required for expression")
        if vtype in {"notNull", "notEmpty"} and any(val.get(k) is not None for k in ("value", "values", "expression")):
            err(errors, file, f"{loc}: {vtype} must not define value/values/expression")
    typed = {v.get("type"): v.get("value") for v in as_list(values) if isinstance(v, Mapping) and v.get("type") in {"min", "max", "minLength", "maxLength"}}
    if isinstance(typed.get("min"), (int, float)) and not isinstance(typed.get("min"), bool) and isinstance(typed.get("max"), (int, float)) and not isinstance(typed.get("max"), bool) and typed["min"] > typed["max"]:
        err(errors, file, f"{where}: min must be <= max")
    if isinstance(typed.get("minLength"), int) and not isinstance(typed.get("minLength"), bool) and isinstance(typed.get("maxLength"), int) and not isinstance(typed.get("maxLength"), bool) and typed["minLength"] > typed["maxLength"]:
        err(errors, file, f"{where}: minLength must be <= maxLength")


def cycle_nodes(graph: Mapping[str, Set[str]]) -> Set[str]:
    state: Dict[str, int] = {}
    stack: List[str] = []
    found: Set[str] = set()

    def visit(node: str) -> None:
        st = state.get(node, 0)
        if st == 1:
            if node in stack:
                idx = stack.index(node)
                found.update(stack[idx:])
            else:
                found.add(node)
            return
        if st == 2:
            return
        state[node] = 1
        stack.append(node)
        for nxt in graph.get(node, set()):
            if nxt in graph:
                visit(nxt)
        stack.pop()
        state[node] = 2

    for n in graph:
        if state.get(n, 0) == 0:
            visit(n)
    return found



from dataclasses import dataclass, field


@dataclass
class ValidationContext:
    """Per-validation indexes and diagnostics; never shared between calls."""
    source: Path
    errors: list = field(default_factory=list)
    warnings: list = field(default_factory=list)
    ids: dict = field(default_factory=dict)
    docs: list = field(default_factory=list)
    data_defs: dict = field(default_factory=dict)
    view_defs: dict = field(default_factory=dict)
    rule_view_defs: dict = field(default_factory=dict)
    api_defs: dict = field(default_factory=dict)
    enum_defs: dict = field(default_factory=dict)
    system_defs: dict = field(default_factory=dict)
    information_defs: dict = field(default_factory=dict)
    datasource_names: set = field(default_factory=set)
    connection_names: set = field(default_factory=set)
    rule_view_codes: set = field(default_factory=set)
