"""Focused cross-file validation passes over a shared ValidationContext."""
from __future__ import annotations
from typing import Any, Mapping
from validation_common import ValidationContext, as_list, named_entries, err, warn, RELATIONS, API_REF_RE

def validate_datasources(ctx: ValidationContext) -> None:
    # Cross-file config refs.
    if ctx.datasource_names:
        for file, root, kind in ctx.docs:
            if kind == "config":
                ci = root.get("connectionInfo")
                conns = ci.get("connections") if isinstance(ci, Mapping) else root.get("connections")
                for i, con in enumerate(as_list(conns)):
                    if isinstance(con, Mapping):
                        for ref in as_list(con.get("dataSources")):
                            refname = ref.get("ref", ref.get("name")) if isinstance(ref, Mapping) else ref
                            if refname and str(refname) not in ctx.datasource_names:
                                err(ctx.errors, file, f"connections[{i}].dataSources: DataSource {refname!r} not found")
    # DataSource mapping refs.
    for name, (file, data) in ctx.data_defs.items():
        for ti, table in enumerate(as_list(data.get("tables"))):
            if isinstance(table, Mapping) and table.get("dataSource"):
                ds = str(table["dataSource"])
                if ctx.datasource_names and ds not in ctx.datasource_names:
                    err(ctx.errors, file, f"data {name}.tables[{ti}].dataSource: {ds!r} not found in loaded config")
                elif not ctx.datasource_names:
                    warn(ctx.warnings, file, f"data {name}.tables[{ti}].dataSource cross-file check skipped because no config YAML was loaded")



def validate_data_ownership(ctx: ValidationContext) -> None:
    # Data ownership.
    for dname, (file, data) in ctx.data_defs.items():
        owner_system = str(data.get("system")) if data.get("system") else ""
        if ctx.system_defs and owner_system and owner_system not in ctx.system_defs:
            err(ctx.errors, file, f"Data {dname}.system: System {owner_system!r} not found")
        elif owner_system and not ctx.system_defs:
            warn(ctx.warnings, file, f"Data {dname}.system cross-file validation skipped because no systems YAML was loaded")
        if owner_system in ctx.system_defs:
            declared = {str(x.get("name") if isinstance(x, Mapping) else x) for x in as_list(ctx.system_defs[owner_system][1].get("dataRefs"))}
            if declared and dname not in declared:
                warn(ctx.warnings, file, f"Data {dname}.system={owner_system} but System.dataRefs does not include it")


def validate_enum_references(ctx: ValidationContext) -> None:
    # Enum bindings on Data columns / API fields.
    for name, (file, data) in ctx.data_defs.items():
        for ti, table in enumerate(as_list(data.get("tables"))):
            if not isinstance(table, Mapping):
                continue
            for cn, cv in named_entries(table.get("columns") or {}):
                if isinstance(cv, Mapping) and cv.get("relEnum"):
                    ref = str(cv.get("relEnum"))
                    if ctx.enum_defs and ref not in ctx.enum_defs:
                        err(ctx.errors, file, f"data {name}.tables[{ti}].columns.{cn}.relEnum: Enum {ref!r} not found")
                    elif not ctx.enum_defs:
                        warn(ctx.warnings, file, f"data {name}.tables[{ti}].columns.{cn}.relEnum cross-file check skipped because no enum YAML was loaded")

    for key, (file, api) in ctx.api_defs.items():
        request = api.get("request")
        if isinstance(request, Mapping):
            for pi, param in enumerate(as_list(request.get("params"))):
                if isinstance(param, Mapping) and param.get("relEnum"):
                    ref = str(param.get("relEnum"))
                    if ctx.enum_defs and ref not in ctx.enum_defs:
                        err(ctx.errors, file, f"API {key}.request.params[{pi}].relEnum: Enum {ref!r} not found")
                    elif not ctx.enum_defs:
                        warn(ctx.warnings, file, f"API {key}.request.params[{pi}].relEnum cross-file check skipped because no enum YAML was loaded")
        response = api.get("response")
        if isinstance(response, Mapping):
            for fi, field in enumerate(as_list(response.get("fields"))):
                if isinstance(field, Mapping) and field.get("relEnum"):
                    ref = str(field.get("relEnum"))
                    if ctx.enum_defs and ref not in ctx.enum_defs:
                        err(ctx.errors, file, f"API {key}.response.fields[{fi}].relEnum: Enum {ref!r} not found")
                    elif not ctx.enum_defs:
                        warn(ctx.warnings, file, f"API {key}.response.fields[{fi}].relEnum cross-file check skipped because no enum YAML was loaded")



def validate_views(ctx: ValidationContext) -> None:
    # View ownership, refs and relation semantics.
    for vname, (file, view) in ctx.view_defs.items():
        owner_system = str(view.get("system")) if view.get("system") else ""
        if ctx.system_defs and owner_system and owner_system not in ctx.system_defs:
            err(ctx.errors, file, f"View {vname}.system: System {owner_system!r} not found")
        elif owner_system and not ctx.system_defs:
            warn(ctx.warnings, file, f"View {vname}.system cross-file validation skipped because no systems YAML was loaded")
        target = str(view.get("targetMain")) if view.get("targetMain") else ""
        if ctx.data_defs and target not in ctx.data_defs:
            err(ctx.errors, file, f"View {vname}.targetMain: Data {target!r} not found")
        elif not ctx.data_defs:
            warn(ctx.warnings, file, f"View {vname}: Data cross-file validation skipped because no data YAML was loaded")

        def validate_props(props: Any, current_data: str, where: str) -> None:
            data_props = set(n for n, _ in named_entries(ctx.data_defs[current_data][1].get("properties") or {})) if current_data in ctx.data_defs else set()
            parent_business_names = set(n for n, _ in named_entries(props or {}))
            for pname, raw in named_entries(props or {}):
                if isinstance(raw, Mapping) and raw.get("relation"):
                    relation = raw.get("relation")
                    if relation not in RELATIONS:
                        err(ctx.errors, file, f"{where}.{pname}.relation: must be one of {sorted(RELATIONS)}")
                    child_data = str(raw.get("data")) if raw.get("data") else ""
                    for k in ("data", "key", "relKey", "properties"):
                        if raw.get(k) in (None, "", {}): err(ctx.errors, file, f"{where}.{pname}.{k}: required for relation")
                    if ctx.data_defs and child_data not in ctx.data_defs:
                        err(ctx.errors, file, f"{where}.{pname}.data: Data {child_data!r} not found")
                    if child_data in ctx.data_defs and raw.get("key"):
                        child_props = {n for n, _ in named_entries(ctx.data_defs[child_data][1].get("properties") or {})}
                        if str(raw.get("key")) not in child_props:
                            err(ctx.errors, file, f"{where}.{pname}.key: {raw.get('key')!r} not found in Data {child_data}")
                    # relKey belongs to parent data/property domain in current model.
                    if data_props and raw.get("relKey") and str(raw.get("relKey")) not in data_props and str(raw.get("relKey")) not in parent_business_names:
                        err(ctx.errors, file, f"{where}.{pname}.relKey: {raw.get('relKey')!r} not resolvable in parent context")
                    validate_props(raw.get("properties") or {}, child_data, f"{where}.{pname}.properties")
                else:
                    ref = raw.get("ref", raw.get("refProperty")) if isinstance(raw, Mapping) else raw
                    if data_props and ref is not None and str(ref) not in data_props:
                        err(ctx.errors, file, f"{where}.{pname}: ref {ref!r} not found in Data {current_data}")
        validate_props(view.get("properties") or {}, target, f"View {vname}.properties")


def validate_api_references(ctx: ValidationContext) -> None:
    # API cross-file references.
    for key, (file, api) in ctx.api_defs.items():
        system = str(api.get("system")) if api.get("system") else ""
        if ctx.system_defs and system not in ctx.system_defs:
            err(ctx.errors, file, f"API {key}.system: System {system!r} not found")
        elif not ctx.system_defs:
            warn(ctx.warnings, file, f"API {key}.system cross-file validation skipped because no systems YAML was loaded")
        response = api.get("response")
        if isinstance(response, Mapping) and response.get("modelRef"):
            model_ref = str(response.get("modelRef"))
            if ctx.view_defs and model_ref not in ctx.view_defs:
                err(ctx.errors, file, f"API {key}.response.modelRef: View {model_ref!r} not found")
            elif not ctx.view_defs:
                warn(ctx.warnings, file, f"API {key}.response.modelRef cross-file validation skipped because no view YAML was loaded")


def validate_rule_references(ctx: ValidationContext) -> None:
    # RuleView view references.
    for rvname, (file, rv) in ctx.rule_view_defs.items():
        view_ref = str(rv.get("viewRef")) if rv.get("viewRef") else ""
        if ctx.view_defs and view_ref not in ctx.view_defs:
            err(ctx.errors, file, f"RuleView {rvname}.viewRef: View {view_ref!r} not found")
        elif not ctx.view_defs:
            warn(ctx.warnings, file, f"RuleView {rvname}: View cross-file validation skipped because no view YAML was loaded")
        default_ds = rv.get("dataSource")
        if default_ds:
            ds = str(default_ds)
            if ctx.datasource_names and ds not in ctx.datasource_names:
                err(ctx.errors, file, f"RuleView {rvname}.dataSource: DataSource {ds!r} not found")
            elif not ctx.datasource_names:
                warn(ctx.warnings, file, f"RuleView {rvname}.dataSource cross-file check skipped because no config YAML was loaded")
        for ri, rule in enumerate(as_list(rv.get("rules"))):
            if isinstance(rule, Mapping) and rule.get("dataSource"):
                ds = str(rule.get("dataSource"))
                if ctx.datasource_names and ds not in ctx.datasource_names:
                    err(ctx.errors, file, f"RuleView {rvname}.rules[{ri}].dataSource: DataSource {ds!r} not found")
                elif not ctx.datasource_names:
                    warn(ctx.warnings, file, f"RuleView {rvname}.rules[{ri}].dataSource cross-file check skipped because no config YAML was loaded")
        api_ref = rv.get("apiRef")
        if api_ref:
            api_ref = str(api_ref)
            if not API_REF_RE.match(api_ref):
                err(ctx.errors, file, f"RuleView {rvname}.apiRef: expected system.apiName, got {api_ref!r}")
            elif ctx.api_defs and api_ref not in ctx.api_defs:
                err(ctx.errors, file, f"RuleView {rvname}.apiRef: API {api_ref!r} not found")
            elif not ctx.api_defs:
                warn(ctx.warnings, file, f"RuleView {rvname}.apiRef cross-file validation skipped because no api YAML was loaded")


