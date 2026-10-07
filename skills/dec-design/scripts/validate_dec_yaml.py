#!/usr/bin/env python3
"""Normative validation for DEC canonical YAML.

Validation is intentionally fail-closed when the necessary facts are loaded.
When a referenced fact lives outside the supplied input set, the validator emits
an explicit warning instead of guessing.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple

import yaml

from yaml_to_xml import ConversionError, convert_document, infer_kind, load_yaml

RULE_TYPES = {
    "check", "checkPattern", "checkData", "checkDataPattern",
    "insert", "update", "delete", "get", "query", "dsl", "grammer",
}
RELATIONS = {"one-to-one", "one-to-many"}
KEY_TYPES = {"increment", "set"}
API_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
API_PARAM_LOCATIONS = {"path", "query", "header", "body"}
API_VALIDATION_TYPES = {"notNull", "notEmpty", "min", "max", "minLength", "maxLength", "pattern", "regex", "enum", "expression"}
INFO_REF_RE = re.compile(r"(?<![#\w-])([A-Za-z_][\w-]*\.[A-Za-z_][\w-]*)")
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


def named_entries(value: Any) -> List[Tuple[str, Any]]:
    if isinstance(value, Mapping):
        return [(str(k), v) for k, v in value.items()]
    out: List[Tuple[str, Any]] = []
    for item in as_list(value):
        if isinstance(item, Mapping) and item.get("name"):
            out.append((str(item["name"]), item))
    return out


def extract_property_refs(properties: Any, prefix: str = "") -> Set[str]:
    """Return business property paths (not underlying data refs)."""
    result: Set[str] = set()
    for name, raw in named_entries(properties or {}):
        path = f"{prefix}.{name}" if prefix else name
        result.add(path)
        if isinstance(raw, Mapping) and raw.get("relation"):
            result.update(extract_property_refs(raw.get("properties", {}), path))
    return result


def extract_info_refs(expression: str) -> Set[str]:
    return set(INFO_REF_RE.findall(expression or ""))


def parse_change_assignments(change_data: str) -> List[Tuple[str, Any]]:
    """Parse common DEC changeData assignments into (view property path, value).

    Supported examples:
      status : 1;
      every(orderDetailList, status : 1);
    Unrecognised expressions are intentionally ignored here; grammar validation remains
    the Runtime/Compiler's responsibility.
    """
    text = str(change_data or "")
    out: List[Tuple[str, Any]] = []

    def scalar(raw: str) -> Any:
        v = raw.strip().strip('\"\'')
        if v.lower() == "null": return None
        if v.lower() == "true": return True
        if v.lower() == "false": return False
        if re.fullmatch(r"[-+]?\d+", v):
            try: return int(v)
            except ValueError: pass
        if re.fullmatch(r"[-+]?(?:\d+\.\d*|\d*\.\d+)", v):
            try: return float(v)
            except ValueError: pass
        return v

    consumed: List[Tuple[int, int]] = []
    for m in re.finditer(r"every\(\s*([A-Za-z_][\w-]*)\s*,\s*([A-Za-z_][\w-]*)\s*:\s*([^;)]+)\)", text):
        out.append((f"{m.group(1)}.{m.group(2)}", scalar(m.group(3))))
        consumed.append((m.start(), m.end()))
    masked = list(text)
    for a,b in consumed:
        for i in range(a,b): masked[i] = ' '
    rest = ''.join(masked)
    for m in re.finditer(r"(?:^|;)\s*([A-Za-z_][\w-]*)\s*:\s*([^;]+)", rest, flags=re.MULTILINE):
        out.append((m.group(1), scalar(m.group(2))))
    return out


def _data_property_enum(data_defs: Mapping[str, Tuple[Path, Mapping[str, Any]]], data_name: str, property_name: str) -> Set[str]:
    found: Set[str] = set()
    item = data_defs.get(data_name)
    if not item: return found
    data = item[1]
    for table in as_list(data.get("tables")):
        if not isinstance(table, Mapping): continue
        for _, raw in named_entries(table.get("columns") or {}):
            if isinstance(raw, Mapping):
                ref = raw.get("ref", raw.get("refProperty"))
                if ref is not None and str(ref) == property_name and raw.get("relEnum"):
                    found.add(str(raw.get("relEnum")))
    return found


def resolve_view_property_enum(view: Mapping[str, Any], path: str, data_defs: Mapping[str, Tuple[Path, Mapping[str, Any]]]) -> Set[str]:
    parts = [x for x in str(path).split('.') if x]
    if not parts: return set()
    current_data = str(view.get("targetMain") or "")
    props: Any = view.get("properties") or {}
    for idx, part in enumerate(parts):
        entries = dict(named_entries(props))
        raw = entries.get(part)
        if raw is None: return set()
        last = idx == len(parts)-1
        if isinstance(raw, Mapping) and raw.get("relation"):
            if last: return set()
            current_data = str(raw.get("data") or "")
            props = raw.get("properties") or {}
            continue
        if not last: return set()
        ref = raw.get("ref", raw.get("refProperty")) if isinstance(raw, Mapping) else raw
        if ref in (None, ""): return set()
        return _data_property_enum(data_defs, current_data, str(ref))
    return set()


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


def main(argv: Optional[Sequence[str]] = None) -> int:
    p = argparse.ArgumentParser(description="Validate DEC canonical YAML structure, semantics and cross-file references")
    p.add_argument("input", type=Path)
    p.add_argument("--json", action="store_true")
    args = p.parse_args(argv)

    source = args.input.resolve()
    files = yaml_files(source)
    errors: List[Dict[str, str]] = []
    warnings: List[Dict[str, str]] = []
    ids: Dict[str, str] = {}

    docs: List[Tuple[Path, Dict[str, Any], str]] = []
    for file in files:
        try:
            root = load_yaml(file)
            kind = infer_kind(root)
            convert_document(root)  # unknown-key/type-shape validation
            docs.append((file, root, kind))
        except (ConversionError, yaml.YAMLError) as exc:
            err(errors, file, str(exc))
            continue
        for node_path, node in walk(root):
            ident = node.get("id") if isinstance(node, Mapping) else None
            if ident and isinstance(ident, str) and DESIGN_ID_RE.match(ident):
                prior = ids.get(ident)
                here = f"{file}:{node_path}"
                if prior:
                    err(errors, file, f"duplicate id {ident}: {prior} and {here}")
                else:
                    ids[ident] = here

    # Global indexes.
    data_defs: Dict[str, Tuple[Path, Mapping[str, Any]]] = {}
    view_defs: Dict[str, Tuple[Path, Mapping[str, Any]]] = {}
    rule_view_defs: Dict[str, Tuple[Path, Mapping[str, Any]]] = {}
    api_defs: Dict[str, Tuple[Path, Mapping[str, Any]]] = {}
    enum_defs: Dict[str, Tuple[Path, Mapping[str, Any]]] = {}
    system_defs: Dict[str, Tuple[Path, Mapping[str, Any]]] = {}
    information_defs: Dict[str, Tuple[Path, Mapping[str, Any], str]] = {}
    datasource_names: Set[str] = set()
    connection_names: Set[str] = set()
    rule_view_codes: Set[str] = set()

    # First pass indexes + local validation.
    for file, root, kind in docs:
        if kind == "config":
            info = root.get("dataSourceInfo", root.get("datasourceInfo"))
            sources = root.get("dataSources", root.get("datasources"))
            if isinstance(info, Mapping):
                sources = info.get("dataSources", info.get("datasources"))
            for i, ds in enumerate(as_list(sources)):
                if not isinstance(ds, Mapping):
                    err(errors, file, f"dataSources[{i}]: expected mapping")
                    continue
                name, typ = ds.get("name"), ds.get("type")
                if not name: err(errors, file, f"dataSources[{i}].name: required")
                if not typ: err(errors, file, f"dataSources[{i}].type: required")
                if name:
                    if str(name) in datasource_names: err(errors, file, f"duplicate DataSource name: {name}")
                    datasource_names.add(str(name))
            ci = root.get("connectionInfo")
            conns = root.get("connections")
            if isinstance(ci, Mapping):
                conns = ci.get("connections")
            for i, con in enumerate(as_list(conns)):
                if not isinstance(con, Mapping):
                    err(errors, file, f"connections[{i}]: expected mapping")
                    continue
                name = con.get("name")
                if not name: err(errors, file, f"connections[{i}].name: required")
                refs = as_list(con.get("dataSources"))
                if not refs: err(errors, file, f"connections[{i}].dataSources: at least one required")
                if name:
                    if str(name) in connection_names: err(errors, file, f"duplicate Connection name: {name}")
                    connection_names.add(str(name))

        elif kind == "data":
            for i, raw in enumerate(as_list(root.get("datas"))):
                if not isinstance(raw, Mapping):
                    err(errors, file, f"datas[{i}]: expected mapping")
                    continue
                name = raw.get("name")
                if not name:
                    err(errors, file, f"datas[{i}].name: required")
                    continue
                if str(name) in data_defs:
                    err(errors, file, f"duplicate Data name: {name}")
                data_defs[str(name)] = (file, raw)
                if not raw.get("system"): err(errors, file, f"data {name}.system: required")
                props = raw.get("properties")
                prop_entries = named_entries(props or {})
                if not prop_entries:
                    err(errors, file, f"data {name}.properties: at least one required")
                prop_names = {n for n, _ in prop_entries}
                for pn, pv in prop_entries:
                    if isinstance(pv, Mapping) and not pv.get("type"):
                        err(errors, file, f"data {name}.properties.{pn}.type: required")
                    elif pv is None or pv == "":
                        err(errors, file, f"data {name}.properties.{pn}: type required")
                for ti, table in enumerate(as_list(raw.get("tables"))):
                    if not isinstance(table, Mapping):
                        err(errors, file, f"data {name}.tables[{ti}]: expected mapping")
                        continue
                    for key in ("name", "dataSource", "key", "keyType", "columns"):
                        if table.get(key) in (None, "", [] , {}):
                            err(errors, file, f"data {name}.tables[{ti}].{key}: required")
                    if table.get("keyType") and table.get("keyType") not in KEY_TYPES:
                        err(errors, file, f"data {name}.tables[{ti}].keyType: must be one of {sorted(KEY_TYPES)}")
                    cols = named_entries(table.get("columns") or {})
                    col_names = {n for n, _ in cols}
                    if table.get("key") and str(table.get("key")) not in col_names:
                        err(errors, file, f"data {name}.tables[{ti}].key: {table.get('key')} not found in columns")
                    for cn, cv in cols:
                        ref = cv.get("ref", cv.get("refProperty")) if isinstance(cv, Mapping) else cv
                        if not ref:
                            err(errors, file, f"data {name}.tables[{ti}].columns.{cn}.ref: required")
                        elif str(ref) not in prop_names:
                            err(errors, file, f"data {name}.tables[{ti}].columns.{cn}: ref {ref!r} not found in data properties")

        elif kind == "view":
            for i, raw in enumerate(as_list(root.get("views"))):
                if not isinstance(raw, Mapping):
                    err(errors, file, f"views[{i}]: expected mapping")
                    continue
                name = raw.get("name")
                if not name:
                    err(errors, file, f"views[{i}].name: required")
                    continue
                if str(name) in view_defs: err(errors, file, f"duplicate View name: {name}")
                view_defs[str(name)] = (file, raw)
                if not raw.get("system"): err(errors, file, f"view {name}.system: required")
                if not raw.get("targetMain"): err(errors, file, f"view {name}.targetMain: required")
                if not named_entries(raw.get("properties") or {}): err(errors, file, f"view {name}.properties: at least one required")

        elif kind == "rule":
            for i, rv in enumerate(as_list(root.get("ruleViews", root.get("rule-view-info")))):
                if not isinstance(rv, Mapping):
                    err(errors, file, f"ruleViews[{i}]: expected mapping")
                    continue
                name = rv.get("name")
                if not name:
                    err(errors, file, f"ruleViews[{i}].name: required")
                    continue
                if str(name) in rule_view_defs: err(errors, file, f"duplicate RuleView name: {name}")
                rule_view_defs[str(name)] = (file, rv)
                code = rv.get("code")
                if not code:
                    err(errors, file, f"RuleView {name}.code: required")
                else:
                    code = str(code)
                    if code in rule_view_codes: err(errors, file, f"duplicate RuleView code: {code}")
                    rule_view_codes.add(code)
                if not rv.get("viewRef"): err(errors, file, f"RuleView {name}.viewRef: required")
                rules = as_list(rv.get("rules"))
                if not rules: err(errors, file, f"RuleView {name}.rules: at least one required")
                seen_rules: Set[str] = set()
                for ri, rule in enumerate(rules):
                    if not isinstance(rule, Mapping):
                        err(errors, file, f"RuleView {name}.rules[{ri}]: expected mapping")
                        continue
                    rname = rule.get("name")
                    if rname:
                        if str(rname) in seen_rules: err(errors, file, f"RuleView {name}: duplicate Rule name {rname}")
                        seen_rules.add(str(rname))
                    validate_rule(rule, f"RuleView {name}.rules[{ri}]", file, errors, warnings)

        elif kind == "api":
            for ai, api in enumerate(as_list(root.get("apis"))):
                if not isinstance(api, Mapping):
                    err(errors, file, f"apis[{ai}]: expected mapping")
                    continue
                name = api.get("name")
                system = api.get("system")
                url = api.get("url")
                method = api.get("method")
                if not name: err(errors, file, f"apis[{ai}].name: required")
                if not system: err(errors, file, f"apis[{ai}].system: required")
                if not url: err(errors, file, f"apis[{ai}].url: required")
                if not method: err(errors, file, f"apis[{ai}].method: required")
                elif str(method) not in API_METHODS: err(errors, file, f"apis[{ai}].method: must be one of {sorted(API_METHODS)}")
                if not name or not system:
                    continue
                key = f"{system}.{name}"
                if key in api_defs:
                    err(errors, file, f"duplicate ApiKey: {key}")
                api_defs[key] = (file, api)
                request = api.get("request")
                if isinstance(request, Mapping):
                    validate_api_validations(request.get("validations"), f"API {key}.request.validations", file, errors)
                params = as_list(request.get("params")) if isinstance(request, Mapping) else []
                param_names: Set[str] = set()
                path_names: Set[str] = set()
                for pi, param in enumerate(params):
                    if not isinstance(param, Mapping):
                        err(errors, file, f"API {key}.request.params[{pi}]: expected mapping")
                        continue
                    pname = param.get("name")
                    loc = param.get("in")
                    ptype = param.get("type")
                    if not pname: err(errors, file, f"API {key}.request.params[{pi}].name: required")
                    if not loc: err(errors, file, f"API {key}.request.params[{pi}].in: required")
                    elif loc not in API_PARAM_LOCATIONS: err(errors, file, f"API {key}.request.params[{pi}].in: must be one of {sorted(API_PARAM_LOCATIONS)}")
                    if not ptype: err(errors, file, f"API {key}.request.params[{pi}].type: required")
                    if pname:
                        pname = str(pname)
                        if pname in param_names: err(errors, file, f"API {key}: duplicate request parameter {pname}")
                        param_names.add(pname)
                        if loc == "path":
                            path_names.add(pname)
                            if param.get("required") is not True:
                                err(errors, file, f"API {key}.request parameter {pname}: path parameter must set required: true")
                            if "default" in param:
                                err(errors, file, f"API {key}.request parameter {pname}: path parameter cannot define default")
                    validate_api_validations(param.get("validations"), f"API {key}.request.params[{pi}].validations", file, errors)
                    if param.get("relEnum") and any(isinstance(v, Mapping) and v.get("type") == "enum" for v in as_list(param.get("validations"))):
                        err(errors, file, f"API {key}.request.params[{pi}]: relEnum and inline enum validation must not both define enum values")
                url_path_names = set(URL_PATH_PARAM_RE.findall(str(url or "")))
                for pname in sorted(url_path_names - path_names):
                    err(errors, file, f"API {key}.url: path variable {{{pname}}} has no matching in:path parameter")
                for pname in sorted(path_names - url_path_names):
                    err(errors, file, f"API {key}.request parameter {pname}: in:path parameter is not present in url")
                response = api.get("response")
                if response is not None:
                    if not isinstance(response, Mapping):
                        err(errors, file, f"API {key}.response: expected mapping")
                    else:
                        if not response.get("type"): err(errors, file, f"API {key}.response.type: required")
                        seen_fields: Set[str] = set()
                        for fi, field in enumerate(as_list(response.get("fields"))):
                            if not isinstance(field, Mapping):
                                err(errors, file, f"API {key}.response.fields[{fi}]: expected mapping")
                                continue
                            fname = field.get("name")
                            if not fname: err(errors, file, f"API {key}.response.fields[{fi}].name: required")
                            if not field.get("type"): err(errors, file, f"API {key}.response.fields[{fi}].type: required")
                            if fname:
                                if str(fname) in seen_fields: err(errors, file, f"API {key}: duplicate response field {fname}")
                                seen_fields.add(str(fname))
                            validate_api_validations(field.get("validations"), f"API {key}.response.fields[{fi}].validations", file, errors)
                            if field.get("relEnum") and any(isinstance(v, Mapping) and v.get("type") == "enum" for v in as_list(field.get("validations"))):
                                err(errors, file, f"API {key}.response.fields[{fi}]: relEnum and inline enum validation must not both define enum values")

        elif kind == "enum":
            for ei, enum in enumerate(as_list(root.get("enums"))):
                if not isinstance(enum, Mapping):
                    err(errors, file, f"enums[{ei}]: expected mapping")
                    continue
                name = enum.get("name")
                if not name:
                    err(errors, file, f"enums[{ei}].name: required")
                    continue
                name = str(name)
                if name in enum_defs:
                    err(errors, file, f"duplicate Enum name: {name}")
                enum_defs[name] = (file, enum)
                values = as_list(enum.get("values"))
                if not values:
                    err(errors, file, f"Enum {name}.values: at least one required")
                seen_value_names: Set[str] = set()
                seen_values: Set[str] = set()
                for vi, item in enumerate(values):
                    if not isinstance(item, Mapping):
                        err(errors, file, f"Enum {name}.values[{vi}]: expected mapping")
                        continue
                    if item.get("value") is None:
                        err(errors, file, f"Enum {name}.values[{vi}].value: required")
                    if not item.get("name"):
                        err(errors, file, f"Enum {name}.values[{vi}].name: required")
                    if item.get("value") is not None:
                        sval = str(item.get("value"))
                        if sval in seen_values: err(errors, file, f"Enum {name}: duplicate value {sval}")
                        seen_values.add(sval)
                    if item.get("name"):
                        sname = str(item.get("name"))
                        if sname in seen_value_names: err(errors, file, f"Enum {name}: duplicate value name {sname}")
                        seen_value_names.add(sname)

        elif kind == "systems":
            for si, system in enumerate(as_list(root.get("systems"))):
                if not isinstance(system, Mapping):
                    err(errors, file, f"systems[{si}]: expected mapping")
                    continue
                sname = system.get("name")
                if not sname:
                    err(errors, file, f"systems[{si}].name: required")
                    continue
                sname = str(sname)
                if sname in system_defs: err(errors, file, f"duplicate System name: {sname}")
                system_defs[sname] = (file, system)
                seen_local: Set[str] = set()
                for ii, info in enumerate(as_list(system.get("information"))):
                    if not isinstance(info, Mapping):
                        err(errors, file, f"System {sname}.information[{ii}]: expected mapping")
                        continue
                    iname = info.get("name")
                    if not iname:
                        err(errors, file, f"System {sname}.information[{ii}].name: required")
                        continue
                    iname = str(iname)
                    if iname in seen_local: err(errors, file, f"System {sname}: duplicate Information local name {iname}")
                    seen_local.add(iname)
                    ikey = f"{sname}.{iname}"
                    information_defs[ikey] = (file, info, sname)
                    recognizers = [k for k in ("ruleRef", "ruleData", "expression") if info.get(k) not in (None, "")]
                    if len(recognizers) != 1:
                        err(errors, file, f"Information {ikey}: exactly one of ruleRef/ruleData/expression is required")
                    if info.get("expression"):
                        for forbidden in ("viewRef", "ruleRef", "ruleData", "changeData"):
                            if info.get(forbidden) not in (None, ""):
                                err(errors, file, f"Information {ikey}: composite expression cannot use {forbidden}")
                    elif not info.get("viewRef"):
                        err(errors, file, f"Information {ikey}.viewRef: required for atomic Information")

        elif kind == "business":
            business = root.get("business")
            if not isinstance(business, Mapping):
                err(errors, file, "business: expected mapping")
                continue
            if not business.get("name"): err(errors, file, "business.name: required")
            dirs = as_list(business.get("directories"))
            if not dirs: err(errors, file, "business.directories: at least one required")
            names: Set[str] = set()
            root_count = 0
            for di, directory in enumerate(dirs):
                if not isinstance(directory, Mapping):
                    err(errors, file, f"business.directories[{di}]: expected mapping")
                    continue
                dname = directory.get("name")
                if not dname:
                    err(errors, file, f"business.directories[{di}].name: required")
                    continue
                dname = str(dname)
                if dname in names: err(errors, file, f"duplicate Directory name: {dname}")
                names.add(dname)
                if directory.get("isRoot") is True: root_count += 1
                if not directory.get("informationRef"): err(errors, file, f"Directory {dname}.informationRef: required")
                if not directory.get("modelRef"): err(errors, file, f"Directory {dname}.modelRef: required")
                for ai, action in enumerate(as_list(directory.get("actions"))):
                    if not isinstance(action, Mapping):
                        err(errors, file, f"Directory {dname}.actions[{ai}]: expected mapping")
                        continue
                    if not action.get("name"): err(errors, file, f"Directory {dname}.actions[{ai}].name: required")
                    if action.get("ruleRef") and not action.get("systemRef"):
                        err(errors, file, f"Directory {dname}.actions[{ai}].systemRef: required when ruleRef is used in P3 BusinessScope")
                    for pi, prod in enumerate(as_list(action.get("produces"))):
                        if not isinstance(prod, Mapping) or not prod.get("ref"):
                            err(errors, file, f"Directory {dname}.actions[{ai}].produces[{pi}].ref: required")
                for subi, sub in enumerate(as_list(directory.get("subDirectories"))):
                    if not isinstance(sub, Mapping) or not sub.get("rel"):
                        err(errors, file, f"Directory {dname}.subDirectories[{subi}].rel: required")
            if root_count == 0:
                err(errors, file, "business.directories: at least one isRoot: true directory is required")
            elif root_count > 1:
                warn(warnings, file, f"business.directories: {root_count} roots found; verify multiple roots are intentionally supported")

    # Cross-file config refs.
    if datasource_names:
        for file, root, kind in docs:
            if kind == "config":
                ci = root.get("connectionInfo")
                conns = ci.get("connections") if isinstance(ci, Mapping) else root.get("connections")
                for i, con in enumerate(as_list(conns)):
                    if isinstance(con, Mapping):
                        for ref in as_list(con.get("dataSources")):
                            refname = ref.get("ref", ref.get("name")) if isinstance(ref, Mapping) else ref
                            if refname and str(refname) not in datasource_names:
                                err(errors, file, f"connections[{i}].dataSources: DataSource {refname!r} not found")
    # DataSource mapping refs.
    for name, (file, data) in data_defs.items():
        for ti, table in enumerate(as_list(data.get("tables"))):
            if isinstance(table, Mapping) and table.get("dataSource"):
                ds = str(table["dataSource"])
                if datasource_names and ds not in datasource_names:
                    err(errors, file, f"data {name}.tables[{ti}].dataSource: {ds!r} not found in loaded config")
                elif not datasource_names:
                    warn(warnings, file, f"data {name}.tables[{ti}].dataSource cross-file check skipped because no config YAML was loaded")


    # Data ownership.
    for dname, (file, data) in data_defs.items():
        owner_system = str(data.get("system")) if data.get("system") else ""
        if system_defs and owner_system and owner_system not in system_defs:
            err(errors, file, f"Data {dname}.system: System {owner_system!r} not found")
        elif owner_system and not system_defs:
            warn(warnings, file, f"Data {dname}.system cross-file validation skipped because no systems YAML was loaded")
        if owner_system in system_defs:
            declared = {str(x.get("name") if isinstance(x, Mapping) else x) for x in as_list(system_defs[owner_system][1].get("dataRefs"))}
            if declared and dname not in declared:
                warn(warnings, file, f"Data {dname}.system={owner_system} but System.dataRefs does not include it")

    # Enum bindings on Data columns / API fields.
    for name, (file, data) in data_defs.items():
        for ti, table in enumerate(as_list(data.get("tables"))):
            if not isinstance(table, Mapping):
                continue
            for cn, cv in named_entries(table.get("columns") or {}):
                if isinstance(cv, Mapping) and cv.get("relEnum"):
                    ref = str(cv.get("relEnum"))
                    if enum_defs and ref not in enum_defs:
                        err(errors, file, f"data {name}.tables[{ti}].columns.{cn}.relEnum: Enum {ref!r} not found")
                    elif not enum_defs:
                        warn(warnings, file, f"data {name}.tables[{ti}].columns.{cn}.relEnum cross-file check skipped because no enum YAML was loaded")

    for key, (file, api) in api_defs.items():
        request = api.get("request")
        if isinstance(request, Mapping):
            for pi, param in enumerate(as_list(request.get("params"))):
                if isinstance(param, Mapping) and param.get("relEnum"):
                    ref = str(param.get("relEnum"))
                    if enum_defs and ref not in enum_defs:
                        err(errors, file, f"API {key}.request.params[{pi}].relEnum: Enum {ref!r} not found")
                    elif not enum_defs:
                        warn(warnings, file, f"API {key}.request.params[{pi}].relEnum cross-file check skipped because no enum YAML was loaded")
        response = api.get("response")
        if isinstance(response, Mapping):
            for fi, field in enumerate(as_list(response.get("fields"))):
                if isinstance(field, Mapping) and field.get("relEnum"):
                    ref = str(field.get("relEnum"))
                    if enum_defs and ref not in enum_defs:
                        err(errors, file, f"API {key}.response.fields[{fi}].relEnum: Enum {ref!r} not found")
                    elif not enum_defs:
                        warn(warnings, file, f"API {key}.response.fields[{fi}].relEnum cross-file check skipped because no enum YAML was loaded")


    # View ownership, refs and relation semantics.
    for vname, (file, view) in view_defs.items():
        owner_system = str(view.get("system")) if view.get("system") else ""
        if system_defs and owner_system and owner_system not in system_defs:
            err(errors, file, f"View {vname}.system: System {owner_system!r} not found")
        elif owner_system and not system_defs:
            warn(warnings, file, f"View {vname}.system cross-file validation skipped because no systems YAML was loaded")
        target = str(view.get("targetMain")) if view.get("targetMain") else ""
        if data_defs and target not in data_defs:
            err(errors, file, f"View {vname}.targetMain: Data {target!r} not found")
        elif not data_defs:
            warn(warnings, file, f"View {vname}: Data cross-file validation skipped because no data YAML was loaded")

        def validate_props(props: Any, current_data: str, where: str) -> None:
            data_props = set(n for n, _ in named_entries(data_defs[current_data][1].get("properties") or {})) if current_data in data_defs else set()
            parent_business_names = set(n for n, _ in named_entries(props or {}))
            for pname, raw in named_entries(props or {}):
                if isinstance(raw, Mapping) and raw.get("relation"):
                    relation = raw.get("relation")
                    if relation not in RELATIONS:
                        err(errors, file, f"{where}.{pname}.relation: must be one of {sorted(RELATIONS)}")
                    child_data = str(raw.get("data")) if raw.get("data") else ""
                    for k in ("data", "key", "relKey", "properties"):
                        if raw.get(k) in (None, "", {}): err(errors, file, f"{where}.{pname}.{k}: required for relation")
                    if data_defs and child_data not in data_defs:
                        err(errors, file, f"{where}.{pname}.data: Data {child_data!r} not found")
                    if child_data in data_defs and raw.get("key"):
                        child_props = {n for n, _ in named_entries(data_defs[child_data][1].get("properties") or {})}
                        if str(raw.get("key")) not in child_props:
                            err(errors, file, f"{where}.{pname}.key: {raw.get('key')!r} not found in Data {child_data}")
                    # relKey belongs to parent data/property domain in current model.
                    if data_props and raw.get("relKey") and str(raw.get("relKey")) not in data_props and str(raw.get("relKey")) not in parent_business_names:
                        err(errors, file, f"{where}.{pname}.relKey: {raw.get('relKey')!r} not resolvable in parent context")
                    validate_props(raw.get("properties") or {}, child_data, f"{where}.{pname}.properties")
                else:
                    ref = raw.get("ref", raw.get("refProperty")) if isinstance(raw, Mapping) else raw
                    if data_props and ref is not None and str(ref) not in data_props:
                        err(errors, file, f"{where}.{pname}: ref {ref!r} not found in Data {current_data}")
        validate_props(view.get("properties") or {}, target, f"View {vname}.properties")

    # API cross-file references.
    for key, (file, api) in api_defs.items():
        system = str(api.get("system")) if api.get("system") else ""
        if system_defs and system not in system_defs:
            err(errors, file, f"API {key}.system: System {system!r} not found")
        elif not system_defs:
            warn(warnings, file, f"API {key}.system cross-file validation skipped because no systems YAML was loaded")
        response = api.get("response")
        if isinstance(response, Mapping) and response.get("modelRef"):
            model_ref = str(response.get("modelRef"))
            if view_defs and model_ref not in view_defs:
                err(errors, file, f"API {key}.response.modelRef: View {model_ref!r} not found")
            elif not view_defs:
                warn(warnings, file, f"API {key}.response.modelRef cross-file validation skipped because no view YAML was loaded")

    # RuleView view references.
    for rvname, (file, rv) in rule_view_defs.items():
        view_ref = str(rv.get("viewRef")) if rv.get("viewRef") else ""
        if view_defs and view_ref not in view_defs:
            err(errors, file, f"RuleView {rvname}.viewRef: View {view_ref!r} not found")
        elif not view_defs:
            warn(warnings, file, f"RuleView {rvname}: View cross-file validation skipped because no view YAML was loaded")
        default_ds = rv.get("dataSource")
        if default_ds:
            ds = str(default_ds)
            if datasource_names and ds not in datasource_names:
                err(errors, file, f"RuleView {rvname}.dataSource: DataSource {ds!r} not found")
            elif not datasource_names:
                warn(warnings, file, f"RuleView {rvname}.dataSource cross-file check skipped because no config YAML was loaded")
        for ri, rule in enumerate(as_list(rv.get("rules"))):
            if isinstance(rule, Mapping) and rule.get("dataSource"):
                ds = str(rule.get("dataSource"))
                if datasource_names and ds not in datasource_names:
                    err(errors, file, f"RuleView {rvname}.rules[{ri}].dataSource: DataSource {ds!r} not found")
                elif not datasource_names:
                    warn(warnings, file, f"RuleView {rvname}.rules[{ri}].dataSource cross-file check skipped because no config YAML was loaded")
        api_ref = rv.get("apiRef")
        if api_ref:
            api_ref = str(api_ref)
            if not API_REF_RE.match(api_ref):
                err(errors, file, f"RuleView {rvname}.apiRef: expected system.apiName, got {api_ref!r}")
            elif api_defs and api_ref not in api_defs:
                err(errors, file, f"RuleView {rvname}.apiRef: API {api_ref!r} not found")
            elif not api_defs:
                warn(warnings, file, f"RuleView {rvname}.apiRef cross-file validation skipped because no api YAML was loaded")

    # System references + Information graph.
    graph: Dict[str, Set[str]] = {}
    for sname, (file, system) in system_defs.items():
        for ref in as_list(system.get("dataRefs")):
            name = ref.get("name") if isinstance(ref, Mapping) else ref
            if data_defs and name and str(name) not in data_defs:
                err(errors, file, f"System {sname}.dataRefs: Data {name!r} not found")
        for ref in as_list(system.get("viewRefs")):
            name = ref.get("name") if isinstance(ref, Mapping) else ref
            if view_defs and name and str(name) not in view_defs:
                err(errors, file, f"System {sname}.viewRefs: View {name!r} not found")

    for ikey, (file, info, owner) in information_defs.items():
        if info.get("expression"):
            refs = extract_info_refs(str(info.get("expression")))
            graph[ikey] = refs
            for ref in refs:
                if ref not in information_defs:
                    err(errors, file, f"Information {ikey}.expression: referenced Information {ref!r} not found")
        else:
            graph[ikey] = set()
            vref = str(info.get("viewRef")) if info.get("viewRef") else ""
            system = system_defs.get(owner, (None, {}))[1]
            declared_views = {str(x.get("name") if isinstance(x, Mapping) else x) for x in as_list(system.get("viewRefs"))}
            if declared_views and vref not in declared_views:
                err(errors, file, f"Information {ikey}.viewRef: {vref!r} is not owned/declared by System {owner}")
            rref = info.get("ruleRef")
            if rref:
                if rule_view_defs and str(rref) not in rule_view_defs:
                    err(errors, file, f"Information {ikey}.ruleRef: RuleView {rref!r} not found")
                elif str(rref) in rule_view_defs:
                    rv_view = str(rule_view_defs[str(rref)][1].get("viewRef"))
                    if vref and rv_view != vref:
                        err(errors, file, f"Information {ikey}: viewRef {vref!r} incompatible with RuleView {rref!r}.viewRef {rv_view!r}")
                elif not rule_view_defs:
                    warn(warnings, file, f"Information {ikey}.ruleRef cross-file validation skipped because no rule YAML was loaded")
    cyc = cycle_nodes(graph)
    if cyc:
        err(errors, source, "Information dependency cycle: " + ", ".join(sorted(cyc)))

    # Information changeData ↔ Enum consistency. When a changed View property
    # ultimately maps to a Data column with relEnum, the assigned value must be
    # one of that Enum's concrete values. This keeps state materialization and
    # the shared Enum fact aligned.
    for ikey, (file, info, owner) in information_defs.items():
        change_data = info.get("changeData")
        view_ref = str(info.get("viewRef") or "")
        if not change_data or not view_ref or view_ref not in view_defs:
            continue
        view = view_defs[view_ref][1]
        for prop_path, changed_value in parse_change_assignments(str(change_data)):
            enum_refs = resolve_view_property_enum(view, prop_path, data_defs)
            if len(enum_refs) > 1:
                err(errors, file, f"Information {ikey}.changeData {prop_path}: maps to multiple Enums {sorted(enum_refs)}")
                continue
            if not enum_refs:
                continue
            enum_ref = next(iter(enum_refs))
            if enum_defs and enum_ref not in enum_defs:
                err(errors, file, f"Information {ikey}.changeData {prop_path}: Enum {enum_ref!r} not found")
                continue
            if enum_ref in enum_defs:
                values = [v.get("value") for v in as_list(enum_defs[enum_ref][1].get("values")) if isinstance(v, Mapping)]
                if not any(str(v) == str(changed_value) for v in values):
                    err(errors, file, f"Information {ikey}.changeData {prop_path}: value {changed_value!r} is not defined by Enum {enum_ref}")
            elif not enum_defs:
                warn(warnings, file, f"Information {ikey}.changeData {prop_path}: Enum {enum_ref!r} validation skipped because no enum YAML was loaded")

    # ModelAccess.
    for sname, (file, system) in system_defs.items():
        for mi, ma in enumerate(as_list(system.get("modelAccess"))):
            if not isinstance(ma, Mapping):
                continue
            model_ref = ma.get("modelRef")
            if not model_ref:
                err(errors, file, f"System {sname}.modelAccess[{mi}].modelRef: required")
                continue
            model_ref = str(model_ref)
            if view_defs and model_ref not in view_defs:
                err(errors, file, f"System {sname}.modelAccess[{mi}].modelRef: View {model_ref!r} not found")
            for mode in ("read", "write"):
                for ai, access in enumerate(as_list(ma.get(mode))):
                    if not isinstance(access, Mapping):
                        err(errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}]: expected mapping")
                        continue
                    if not access.get("path"):
                        err(errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}].path: required")
                    refs = access.get("refs") if access.get("refs") is not None else access.get("ref")
                    ref_items = as_list(refs)
                    if not ref_items:
                        err(errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}].ref: at least one required")
                    for ri, ref in enumerate(ref_items):
                        if not isinstance(ref, Mapping):
                            err(errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}].ref[{ri}]: expected mapping")
                            continue
                        v = ref.get("view")
                        prop = ref.get("property")
                        if not v: err(errors, file, f"...ref[{ri}].view: required")
                        if not prop: err(errors, file, f"...ref[{ri}].property: required")
                        if view_defs and v and str(v) not in view_defs:
                            err(errors, file, f"System {sname}.modelAccess ref view {v!r} not found")
                        if view_defs and v and str(v) in view_defs and prop:
                            view = view_defs[str(v)][1]
                            valid_props = extract_property_refs(view.get("properties") or {})
                            target_main = str(view.get("targetMain", ""))
                            # Current exact binding allows target-main name first, then property path.
                            if str(prop) != target_main and str(prop) not in valid_props:
                                err(errors, file, f"System {sname}.modelAccess ref property {prop!r} not found in View {v} or targetMain {target_main!r}")

    # Business refs.
    for file, root, kind in docs:
        if kind != "business":
            continue
        business = root.get("business") or {}
        dirs = as_list(business.get("directories"))
        names = {str(d.get("name")) for d in dirs if isinstance(d, Mapping) and d.get("name")}
        case_targets: Dict[str, List[Tuple[str, str]]] = defaultdict(list)
        for parent in dirs:
            if not isinstance(parent, Mapping) or not parent.get("name"):
                continue
            for sub in as_list(parent.get("subDirectories")):
                if isinstance(sub, Mapping) and sub.get("role") == "case" and sub.get("rel"):
                    case_targets[str(sub.get("rel"))].append((str(parent.get("name")), str(sub.get("informationRef") or "")))
        for di, directory in enumerate(dirs):
            if not isinstance(directory, Mapping) or not directory.get("name"):
                continue
            dname = str(directory["name"])
            if dname in case_targets and as_list(directory.get("dependencies")):
                parents = ", ".join(p for p, _ in case_targets[dname])
                err(errors, file, f"Directory {dname}.dependencies: case Directory target already receives its gate from {parents}; do not duplicate depend conditions")
            model_ref = directory.get("modelRef")
            if view_defs and model_ref and str(model_ref) not in view_defs:
                err(errors, file, f"Directory {dname}.modelRef: View {model_ref!r} not found")
            elif not view_defs and model_ref:
                warn(warnings, file, f"Directory {dname}.modelRef cross-file validation skipped because no view YAML was loaded")

            info_refs: List[Tuple[str, str]] = []
            if directory.get("informationRef"): info_refs.append(("informationRef", str(directory["informationRef"])))
            change = directory.get("change")
            if isinstance(change, Mapping) and change.get("informationRef"):
                info_refs.append(("change.informationRef", str(change["informationRef"])))
            for depi, dep in enumerate(as_list(directory.get("dependencies"))):
                if isinstance(dep, Mapping) and dep.get("informationRef"):
                    info_refs.append((f"dependencies[{depi}]", str(dep["informationRef"])))
            for subi, sub in enumerate(as_list(directory.get("subDirectories"))):
                if not isinstance(sub, Mapping):
                    continue
                rel = sub.get("rel")
                if rel and str(rel) not in names:
                    err(errors, file, f"Directory {dname}.subDirectories[{subi}].rel: Directory {rel!r} not found")
                if rel and str(rel) == dname:
                    err(errors, file, f"Directory {dname}.subDirectories[{subi}]: self relation is not allowed")
                if sub.get("informationRef"):
                    info_refs.append((f"subDirectories[{subi}].informationRef", str(sub["informationRef"])))
                back = sub.get("back")
                if isinstance(back, Mapping):
                    if not back.get("name"): err(errors, file, f"Directory {dname}.subDirectories[{subi}].back.name: required")
                    for bai, action in enumerate(as_list(back.get("actions"))):
                        if isinstance(action, Mapping) and action.get("ruleRef") and not action.get("systemRef"):
                            err(errors, file, f"Directory {dname}.back.actions[{bai}].systemRef: required when ruleRef is used")
            for ai, action in enumerate(as_list(directory.get("actions"))):
                if not isinstance(action, Mapping):
                    continue
                sref = action.get("systemRef")
                rref = action.get("ruleRef")
                if rref:
                    if system_defs and str(sref) not in system_defs:
                        err(errors, file, f"Directory {dname}.actions[{ai}].systemRef: System {sref!r} not found")
                    if rule_view_defs and str(rref) not in rule_view_defs:
                        err(errors, file, f"Directory {dname}.actions[{ai}].ruleRef: RuleView {rref!r} not found")
                    if str(rref) in rule_view_defs and model_ref:
                        rv_view = str(rule_view_defs[str(rref)][1].get("viewRef"))
                        if rv_view != str(model_ref):
                            err(errors, file, f"Directory {dname}.actions[{ai}]: RuleView {rref}.viewRef {rv_view!r} incompatible with directory modelRef {model_ref!r}")
                for pi, prod in enumerate(as_list(action.get("produces"))):
                    if isinstance(prod, Mapping) and prod.get("informationRef"):
                        ref = str(prod["informationRef"])
                        info_refs.append((f"actions[{ai}].produces[{pi}].informationRef", ref))
                        if ref in information_defs and information_defs[ref][1].get("expression"):
                            err(errors, file, f"Directory {dname}.actions[{ai}].produces[{pi}]: informationRef {ref!r} is composite; Produce should map to an atomic Information")

            if information_defs:
                for loc, ref in info_refs:
                    if ref not in information_defs:
                        err(errors, file, f"Directory {dname}.{loc}: Information {ref!r} not found")
                if isinstance(change, Mapping) and change.get("informationRef"):
                    cref = str(change["informationRef"])
                    if cref in information_defs and not information_defs[cref][1].get("changeData"):
                        warn(warnings, file, f"Directory {dname}.change: Information {cref!r} has no changeData; ensure an Action materializes it")
            elif info_refs:
                warn(warnings, file, f"Directory {dname}: Information cross-file validation skipped because no systems YAML was loaded")

    result = {
        "status": "PASSED" if not errors else "FAILED",
        "files": len(files),
        "stableIds": len(ids),
        "datas": len(data_defs),
        "views": len(view_defs),
        "ruleViews": len(rule_view_defs),
        "apis": len(api_defs),
        "enums": len(enum_defs),
        "systems": len(system_defs),
        "informationKeys": len(information_defs),
        "dataSources": len(datasource_names),
        "connections": len(connection_names),
        "errors": errors,
        "warnings": warnings,
    }
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(
            f"{result['status']}: files={result['files']} ids={result['stableIds']} "
            f"datas={result['datas']} views={result['views']} ruleViews={result['ruleViews']} apis={result['apis']} enums={result['enums']} "
            f"systems={result['systems']} informationKeys={result['informationKeys']}"
        )
        for item in warnings:
            print(f"WARN {item['file']}: {item['message']}")
        for item in errors:
            print(f"ERROR {item['file']}: {item['message']}", file=sys.stderr)
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
