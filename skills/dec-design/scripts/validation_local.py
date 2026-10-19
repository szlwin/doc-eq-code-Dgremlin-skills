"""Kind-specific indexing and local validation, independently callable."""
from __future__ import annotations
from pathlib import Path
from typing import Any, Mapping, Set
from validation_common import ValidationContext, as_list, named_entries, err, warn, validate_rule, validate_api_validations, KEY_TYPES, API_METHODS, API_PARAM_LOCATIONS, URL_PATH_PARAM_RE

def validate_config(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    info = root.get("dataSourceInfo", root.get("datasourceInfo"))
    sources = root.get("dataSources", root.get("datasources"))
    if isinstance(info, Mapping):
        sources = info.get("dataSources", info.get("datasources"))
    for i, ds in enumerate(as_list(sources)):
        if not isinstance(ds, Mapping):
            err(ctx.errors, file, f"dataSources[{i}]: expected mapping")
            continue
        name, typ = ds.get("name"), ds.get("type")
        if not name: err(ctx.errors, file, f"dataSources[{i}].name: required")
        if not typ: err(ctx.errors, file, f"dataSources[{i}].type: required")
        if name:
            if str(name) in ctx.datasource_names: err(ctx.errors, file, f"duplicate DataSource name: {name}")
            ctx.datasource_names.add(str(name))
    ci = root.get("connectionInfo")
    conns = root.get("connections")
    if isinstance(ci, Mapping):
        conns = ci.get("connections")
    for i, con in enumerate(as_list(conns)):
        if not isinstance(con, Mapping):
            err(ctx.errors, file, f"connections[{i}]: expected mapping")
            continue
        name = con.get("name")
        if not name: err(ctx.errors, file, f"connections[{i}].name: required")
        refs = as_list(con.get("dataSources"))
        if not refs: err(ctx.errors, file, f"connections[{i}].dataSources: at least one required")
        if name:
            if str(name) in ctx.connection_names: err(ctx.errors, file, f"duplicate Connection name: {name}")
            ctx.connection_names.add(str(name))


def validate_data(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    for i, raw in enumerate(as_list(root.get("datas"))):
        if not isinstance(raw, Mapping):
            err(ctx.errors, file, f"datas[{i}]: expected mapping")
            continue
        name = raw.get("name")
        if not name:
            err(ctx.errors, file, f"datas[{i}].name: required")
            continue
        if str(name) in ctx.data_defs:
            err(ctx.errors, file, f"duplicate Data name: {name}")
        ctx.data_defs[str(name)] = (file, raw)
        if not raw.get("system"): err(ctx.errors, file, f"data {name}.system: required")
        props = raw.get("properties")
        prop_entries = named_entries(props or {})
        if not prop_entries:
            err(ctx.errors, file, f"data {name}.properties: at least one required")
        prop_names = {n for n, _ in prop_entries}
        for pn, pv in prop_entries:
            if isinstance(pv, Mapping) and not pv.get("type"):
                err(ctx.errors, file, f"data {name}.properties.{pn}.type: required")
            elif pv is None or pv == "":
                err(ctx.errors, file, f"data {name}.properties.{pn}: type required")
        for ti, table in enumerate(as_list(raw.get("tables"))):
            if not isinstance(table, Mapping):
                err(ctx.errors, file, f"data {name}.tables[{ti}]: expected mapping")
                continue
            for key in ("name", "dataSource", "key", "keyType", "columns"):
                if table.get(key) in (None, "", [] , {}):
                    err(ctx.errors, file, f"data {name}.tables[{ti}].{key}: required")
            if table.get("keyType") and table.get("keyType") not in KEY_TYPES:
                err(ctx.errors, file, f"data {name}.tables[{ti}].keyType: must be one of {sorted(KEY_TYPES)}")
            cols = named_entries(table.get("columns") or {})
            col_names = {n for n, _ in cols}
            if table.get("key") and str(table.get("key")) not in col_names:
                err(ctx.errors, file, f"data {name}.tables[{ti}].key: {table.get('key')} not found in columns")
            for cn, cv in cols:
                ref = cv.get("ref", cv.get("refProperty")) if isinstance(cv, Mapping) else cv
                if not ref:
                    err(ctx.errors, file, f"data {name}.tables[{ti}].columns.{cn}.ref: required")
                elif str(ref) not in prop_names:
                    err(ctx.errors, file, f"data {name}.tables[{ti}].columns.{cn}: ref {ref!r} not found in data properties")


def validate_view(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    for i, raw in enumerate(as_list(root.get("views"))):
        if not isinstance(raw, Mapping):
            err(ctx.errors, file, f"views[{i}]: expected mapping")
            continue
        name = raw.get("name")
        if not name:
            err(ctx.errors, file, f"views[{i}].name: required")
            continue
        if str(name) in ctx.view_defs: err(ctx.errors, file, f"duplicate View name: {name}")
        ctx.view_defs[str(name)] = (file, raw)
        if not raw.get("system"): err(ctx.errors, file, f"view {name}.system: required")
        if not raw.get("targetMain"): err(ctx.errors, file, f"view {name}.targetMain: required")
        if not named_entries(raw.get("properties") or {}): err(ctx.errors, file, f"view {name}.properties: at least one required")


def validate_rule_document(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    for i, rv in enumerate(as_list(root.get("ruleViews", root.get("rule-view-info")))):
        if not isinstance(rv, Mapping):
            err(ctx.errors, file, f"ruleViews[{i}]: expected mapping")
            continue
        name = rv.get("name")
        if not name:
            err(ctx.errors, file, f"ruleViews[{i}].name: required")
            continue
        if str(name) in ctx.rule_view_defs: err(ctx.errors, file, f"duplicate RuleView name: {name}")
        ctx.rule_view_defs[str(name)] = (file, rv)
        code = rv.get("code")
        if not code:
            err(ctx.errors, file, f"RuleView {name}.code: required")
        else:
            code = str(code)
            if code in ctx.rule_view_codes: err(ctx.errors, file, f"duplicate RuleView code: {code}")
            ctx.rule_view_codes.add(code)
        if not rv.get("viewRef"): err(ctx.errors, file, f"RuleView {name}.viewRef: required")
        rules = as_list(rv.get("rules"))
        if not rules: err(ctx.errors, file, f"RuleView {name}.rules: at least one required")
        seen_rules: Set[str] = set()
        for ri, rule in enumerate(rules):
            if not isinstance(rule, Mapping):
                err(ctx.errors, file, f"RuleView {name}.rules[{ri}]: expected mapping")
                continue
            rname = rule.get("name")
            if rname:
                if str(rname) in seen_rules: err(ctx.errors, file, f"RuleView {name}: duplicate Rule name {rname}")
                seen_rules.add(str(rname))
            validate_rule(rule, f"RuleView {name}.rules[{ri}]", file, ctx.errors, ctx.warnings)


def validate_api(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    for ai, api in enumerate(as_list(root.get("apis"))):
        if not isinstance(api, Mapping):
            err(ctx.errors, file, f"apis[{ai}]: expected mapping")
            continue
        name = api.get("name")
        system = api.get("system")
        url = api.get("url")
        method = api.get("method")
        if not name: err(ctx.errors, file, f"apis[{ai}].name: required")
        if not system: err(ctx.errors, file, f"apis[{ai}].system: required")
        if not url: err(ctx.errors, file, f"apis[{ai}].url: required")
        if not method: err(ctx.errors, file, f"apis[{ai}].method: required")
        elif str(method) not in API_METHODS: err(ctx.errors, file, f"apis[{ai}].method: must be one of {sorted(API_METHODS)}")
        if not name or not system:
            continue
        key = f"{system}.{name}"
        if key in ctx.api_defs:
            err(ctx.errors, file, f"duplicate ApiKey: {key}")
        ctx.api_defs[key] = (file, api)
        request = api.get("request")
        if isinstance(request, Mapping):
            validate_api_validations(request.get("validations"), f"API {key}.request.validations", file, ctx.errors)
        params = as_list(request.get("params")) if isinstance(request, Mapping) else []
        param_names: Set[str] = set()
        path_names: Set[str] = set()
        for pi, param in enumerate(params):
            if not isinstance(param, Mapping):
                err(ctx.errors, file, f"API {key}.request.params[{pi}]: expected mapping")
                continue
            pname = param.get("name")
            loc = param.get("in")
            ptype = param.get("type")
            if not pname: err(ctx.errors, file, f"API {key}.request.params[{pi}].name: required")
            if not loc: err(ctx.errors, file, f"API {key}.request.params[{pi}].in: required")
            elif loc not in API_PARAM_LOCATIONS: err(ctx.errors, file, f"API {key}.request.params[{pi}].in: must be one of {sorted(API_PARAM_LOCATIONS)}")
            if not ptype: err(ctx.errors, file, f"API {key}.request.params[{pi}].type: required")
            if pname:
                pname = str(pname)
                if pname in param_names: err(ctx.errors, file, f"API {key}: duplicate request parameter {pname}")
                param_names.add(pname)
                if loc == "path":
                    path_names.add(pname)
                    if param.get("required") is not True:
                        err(ctx.errors, file, f"API {key}.request parameter {pname}: path parameter must set required: true")
                    if "default" in param:
                        err(ctx.errors, file, f"API {key}.request parameter {pname}: path parameter cannot define default")
            validate_api_validations(param.get("validations"), f"API {key}.request.params[{pi}].validations", file, ctx.errors)
            if param.get("relEnum") and any(isinstance(v, Mapping) and v.get("type") == "enum" for v in as_list(param.get("validations"))):
                err(ctx.errors, file, f"API {key}.request.params[{pi}]: relEnum and inline enum validation must not both define enum values")
        url_path_names = set(URL_PATH_PARAM_RE.findall(str(url or "")))
        for pname in sorted(url_path_names - path_names):
            err(ctx.errors, file, f"API {key}.url: path variable {{{pname}}} has no matching in:path parameter")
        for pname in sorted(path_names - url_path_names):
            err(ctx.errors, file, f"API {key}.request parameter {pname}: in:path parameter is not present in url")
        response = api.get("response")
        if response is not None:
            if not isinstance(response, Mapping):
                err(ctx.errors, file, f"API {key}.response: expected mapping")
            else:
                if not response.get("type"): err(ctx.errors, file, f"API {key}.response.type: required")
                seen_fields: Set[str] = set()
                for fi, field in enumerate(as_list(response.get("fields"))):
                    if not isinstance(field, Mapping):
                        err(ctx.errors, file, f"API {key}.response.fields[{fi}]: expected mapping")
                        continue
                    fname = field.get("name")
                    if not fname: err(ctx.errors, file, f"API {key}.response.fields[{fi}].name: required")
                    if not field.get("type"): err(ctx.errors, file, f"API {key}.response.fields[{fi}].type: required")
                    if fname:
                        if str(fname) in seen_fields: err(ctx.errors, file, f"API {key}: duplicate response field {fname}")
                        seen_fields.add(str(fname))
                    validate_api_validations(field.get("validations"), f"API {key}.response.fields[{fi}].validations", file, ctx.errors)
                    if field.get("relEnum") and any(isinstance(v, Mapping) and v.get("type") == "enum" for v in as_list(field.get("validations"))):
                        err(ctx.errors, file, f"API {key}.response.fields[{fi}]: relEnum and inline enum validation must not both define enum values")


def validate_enum(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    for ei, enum in enumerate(as_list(root.get("enums"))):
        if not isinstance(enum, Mapping):
            err(ctx.errors, file, f"enums[{ei}]: expected mapping")
            continue
        name = enum.get("name")
        if not name:
            err(ctx.errors, file, f"enums[{ei}].name: required")
            continue
        name = str(name)
        if name in ctx.enum_defs:
            err(ctx.errors, file, f"duplicate Enum name: {name}")
        ctx.enum_defs[name] = (file, enum)
        values = as_list(enum.get("values"))
        if not values:
            err(ctx.errors, file, f"Enum {name}.values: at least one required")
        seen_value_names: Set[str] = set()
        seen_values: Set[str] = set()
        for vi, item in enumerate(values):
            if not isinstance(item, Mapping):
                err(ctx.errors, file, f"Enum {name}.values[{vi}]: expected mapping")
                continue
            if item.get("value") is None:
                err(ctx.errors, file, f"Enum {name}.values[{vi}].value: required")
            elif isinstance(item.get("value"), bool) or not isinstance(item.get("value"), int):
                err(ctx.errors, file, f"Enum {name}.values[{vi}].value: must be an Integer")
            elif not -2147483648 <= item.get("value") <= 2147483647:
                err(ctx.errors, file, f"Enum {name}.values[{vi}].value: must fit Java Integer range")
            if not item.get("name"):
                err(ctx.errors, file, f"Enum {name}.values[{vi}].name: required")
            if item.get("value") is not None:
                sval = str(item.get("value"))
                if sval in seen_values: err(ctx.errors, file, f"Enum {name}: duplicate value {sval}")
                seen_values.add(sval)
            if item.get("name"):
                sname = str(item.get("name"))
                if sname in seen_value_names: err(ctx.errors, file, f"Enum {name}: duplicate value name {sname}")
                seen_value_names.add(sname)


def validate_systems(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    for si, system in enumerate(as_list(root.get("systems"))):
        if not isinstance(system, Mapping):
            err(ctx.errors, file, f"systems[{si}]: expected mapping")
            continue
        sname = system.get("name")
        if not sname:
            err(ctx.errors, file, f"systems[{si}].name: required")
            continue
        sname = str(sname)
        if sname in ctx.system_defs: err(ctx.errors, file, f"duplicate System name: {sname}")
        ctx.system_defs[sname] = (file, system)
        seen_local: Set[str] = set()
        for ii, info in enumerate(as_list(system.get("information"))):
            if not isinstance(info, Mapping):
                err(ctx.errors, file, f"System {sname}.information[{ii}]: expected mapping")
                continue
            iname = info.get("name")
            if not iname:
                err(ctx.errors, file, f"System {sname}.information[{ii}].name: required")
                continue
            iname = str(iname)
            if iname in seen_local: err(ctx.errors, file, f"System {sname}: duplicate Information local name {iname}")
            seen_local.add(iname)
            ikey = f"{sname}.{iname}"
            ctx.information_defs[ikey] = (file, info, sname)
            recognizers = [k for k in ("ruleRef", "ruleData", "expression") if info.get(k) not in (None, "")]
            if len(recognizers) != 1:
                err(ctx.errors, file, f"Information {ikey}: exactly one of ruleRef/ruleData/expression is required")
            if info.get("expression"):
                for forbidden in ("viewRef", "ruleRef", "ruleData", "changeData"):
                    if info.get(forbidden) not in (None, ""):
                        err(ctx.errors, file, f"Information {ikey}: composite expression cannot use {forbidden}")
            elif not info.get("viewRef"):
                err(ctx.errors, file, f"Information {ikey}.viewRef: required for atomic Information")


def validate_business(file: Path, root: Mapping[str, Any], ctx: ValidationContext) -> None:
    business = root.get("business")
    if not isinstance(business, Mapping):
        err(ctx.errors, file, "business: expected mapping")
        return
    if not business.get("name"): err(ctx.errors, file, "business.name: required")
    dirs = as_list(business.get("directories"))
    if not dirs: err(ctx.errors, file, "business.directories: at least one required")
    names: Set[str] = set()
    root_count = 0
    for di, directory in enumerate(dirs):
        if not isinstance(directory, Mapping):
            err(ctx.errors, file, f"business.directories[{di}]: expected mapping")
            continue
        dname = directory.get("name")
        if not dname:
            err(ctx.errors, file, f"business.directories[{di}].name: required")
            continue
        dname = str(dname)
        if dname in names: err(ctx.errors, file, f"duplicate Directory name: {dname}")
        names.add(dname)
        if directory.get("isRoot") is True: root_count += 1
        if not directory.get("informationRef"): err(ctx.errors, file, f"Directory {dname}.informationRef: required")
        if not directory.get("modelRef"): err(ctx.errors, file, f"Directory {dname}.modelRef: required")
        for ai, action in enumerate(as_list(directory.get("actions"))):
            if not isinstance(action, Mapping):
                err(ctx.errors, file, f"Directory {dname}.actions[{ai}]: expected mapping")
                continue
            if not action.get("name"): err(ctx.errors, file, f"Directory {dname}.actions[{ai}].name: required")
            if action.get("ruleRef") and not action.get("systemRef"):
                err(ctx.errors, file, f"Directory {dname}.actions[{ai}].systemRef: required when ruleRef is used in P3 BusinessScope")
            for pi, prod in enumerate(as_list(action.get("produces"))):
                if not isinstance(prod, Mapping) or not prod.get("ref"):
                    err(ctx.errors, file, f"Directory {dname}.actions[{ai}].produces[{pi}].ref: required")
        for subi, sub in enumerate(as_list(directory.get("subDirectories"))):
            if not isinstance(sub, Mapping) or not sub.get("rel"):
                err(ctx.errors, file, f"Directory {dname}.subDirectories[{subi}].rel: required")
    if root_count == 0:
        err(ctx.errors, file, "business.directories: at least one isRoot: true directory is required")
    elif root_count > 1:
        warn(ctx.warnings, file, f"business.directories: {root_count} roots found; verify multiple roots are intentionally supported")


LOCAL_VALIDATORS = {
    "config": validate_config,
    "data": validate_data,
    "view": validate_view,
    "rule": validate_rule_document,
    "api": validate_api,
    "enum": validate_enum,
    "systems": validate_systems,
    "business": validate_business,
}
