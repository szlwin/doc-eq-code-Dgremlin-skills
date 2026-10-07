#!/usr/bin/env python3
"""Deterministic DEC YAML -> DEC XML converter.

The converter intentionally supports a bounded DEC YAML profile and fails on
unknown semantic keys. XML is a derived artifact; edit YAML instead.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple
from xml.dom import minidom

import yaml


class ConversionError(ValueError):
    pass


ROOT_META = {"kind", "version", "metadata", "notes", "description"}
NODE_META = {"id", "notes", "description"}


def _is_map(value: Any) -> bool:
    return isinstance(value, Mapping)


def _map(value: Any, path: str) -> Dict[str, Any]:
    if not isinstance(value, Mapping):
        raise ConversionError(f"{path}: expected mapping, got {type(value).__name__}")
    return dict(value)


def _list(value: Any, path: str) -> List[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def _text(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return ""
    return str(value)


def _require(node: Mapping[str, Any], key: str, path: str) -> Any:
    if key not in node or node[key] is None or node[key] == "":
        raise ConversionError(f"{path}.{key}: required")
    return node[key]


def _reject_unknown(node: Mapping[str, Any], allowed: Iterable[str], path: str) -> None:
    known = set(allowed) | NODE_META
    unknown = sorted(k for k in node.keys() if k not in known)
    if unknown:
        raise ConversionError(f"{path}: unsupported keys: {', '.join(unknown)}")


def _set_attr(el: minidom.Element, xml_name: str, value: Any) -> None:
    if value is not None:
        el.setAttribute(xml_name, _text(value))


def _element(doc: minidom.Document, name: str, attrs: Optional[Mapping[str, Any]] = None) -> minidom.Element:
    el = doc.createElement(name)
    if attrs:
        for key, value in attrs.items():
            _set_attr(el, key, value)
    return el


def _append_text(doc: minidom.Document, parent: minidom.Element, name: str, value: Any) -> None:
    if value is None:
        return
    el = _element(doc, name)
    el.appendChild(doc.createTextNode(_text(value)))
    parent.appendChild(el)


def _append_cdata(doc: minidom.Document, parent: minidom.Element, name: str, value: Any) -> None:
    if value is None:
        return
    el = _element(doc, name)
    el.appendChild(doc.createCDATASection(_text(value).strip("\n")))
    parent.appendChild(el)


def _id_comment(doc: minidom.Document, parent: minidom.Element, node: Mapping[str, Any], emit: bool) -> None:
    if emit and node.get("id"):
        parent.appendChild(doc.createComment(f" dec-id: {_text(node['id'])} "))


def infer_kind(root: Mapping[str, Any]) -> str:
    artifact = root.get("artifact") if isinstance(root, Mapping) else None
    if (
        isinstance(artifact, Mapping)
        and (
            artifact.get("producer") == "html-recovery"
            or any(key in root for key in ("sourceText", "source_rows", "informationTree"))
        )
    ):
        raise ConversionError(
            "HTML recovery artifact is presentation data, not canonical DEC YAML; "
            "author an approved kind: document from the source design"
        )
    explicit = root.get("kind")
    if explicit:
        return str(explicit)
    if "businesses" in root:
        raise ConversionError("legacy declaration YAML (systems + businesses) is not supported by this converter")
    if "directories" in root or root.get("kind") == "directory":
        raise ConversionError("legacy standalone Directory is removed; use business.directories under P3 BusinessScope")

    candidates: List[str] = []
    if "business" in root:
        candidates.append("business")
    if "ruleViews" in root or "rule-view-info" in root:
        candidates.append("rule")
    if "views" in root:
        candidates.append("view")
    if "datas" in root:
        candidates.append("data")
    if "apis" in root:
        candidates.append("api")
    if "enums" in root:
        candidates.append("enum")
    if "systems" in root:
        candidates.append("systems")
    config_keys = {
        "dataSourceInfo", "datasourceInfo", "datasources", "dataSources",
        "connections", "connectionInfo", "dataFiles", "viewFiles", "ruleFiles",
        "relationFiles", "serviceFiles", "apiFiles", "enumFiles", "systemFiles", "businessFiles",
    }
    if any(k in root for k in config_keys):
        candidates.append("config")

    candidates = list(dict.fromkeys(candidates))
    if len(candidates) != 1:
        raise ConversionError(
            "cannot infer a single DEC YAML kind; add kind: config|data|view|rule|api|enum|systems|business"
        )
    return candidates[0]


def convert_document(root: Mapping[str, Any], *, emit_id_comments: bool = False) -> str:
    kind = infer_kind(root)
    allowed_root = ROOT_META | {
        "dataSourceInfo", "datasourceInfo", "datasources", "dataSources", "connections", "connectionInfo",
        "dataFiles", "viewFiles", "ruleFiles", "relationFiles", "serviceFiles", "apiFiles", "enumFiles", "systemFiles", "businessFiles",
        "datas", "views", "ruleViews", "rule-view-info", "apis", "enums", "systems", "business",
    }
    unknown_root = sorted(k for k in root if k not in allowed_root)
    if unknown_root:
        raise ConversionError(f"root: unsupported keys: {', '.join(unknown_root)}")

    doc = minidom.Document()
    if kind == "config":
        root_el = _build_config(doc, root, emit_id_comments)
    elif kind == "data":
        root_el = _build_data(doc, root, emit_id_comments)
    elif kind == "view":
        root_el = _build_view(doc, root, emit_id_comments)
    elif kind == "rule":
        root_el = _build_rule(doc, root, emit_id_comments)
    elif kind == "api":
        root_el = _build_api(doc, root, emit_id_comments)
    elif kind == "enum":
        root_el = _build_enum(doc, root, emit_id_comments)
    elif kind == "systems":
        root_el = _build_systems(doc, root, emit_id_comments)
    elif kind == "business":
        root_el = _build_business(doc, root, emit_id_comments)
    else:
        raise ConversionError(f"unsupported kind: {kind}")
    doc.appendChild(root_el)
    return _serialize_document(doc)


def _serialize_document(doc: minidom.Document) -> str:
    """Serialize XML with stable numeric references for attribute newlines.

    DEC expressions are commonly authored as block scalars.  A literal newline
    in an XML attribute is normalized to a space by XML parsers, while
    ``&#10;`` preserves the exact expression text on import.  Emit the numeric
    reference so YAML -> XML -> YAML does not silently rewrite multiline
    expressions.
    """
    text = doc.toprettyxml(indent="  ", encoding="UTF-8").decode("UTF-8")

    return _encode_multiline_attributes(text)


def _encode_multiline_attributes(text: str) -> str:
    """Replace newlines occurring inside double-quoted XML attributes."""
    out: List[str] = []
    in_quote = False
    for char in text:
        if char == '"':
            in_quote = not in_quote
            out.append(char)
        elif char == "\n" and in_quote:
            out.append("&#10;")
        elif char == "\r" and in_quote:
            continue
        else:
            out.append(char)
    return "".join(out)


def _build_config(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    el = _element(doc, "orm-config")

    source_info = root.get("dataSourceInfo", root.get("datasourceInfo"))
    direct_sources = root.get("dataSources", root.get("datasources"))
    default_source = None
    sources: Any = direct_sources
    if source_info is not None:
        si = _map(source_info, "dataSourceInfo")
        _reject_unknown(si, {"default", "dataSources", "datasources"}, "dataSourceInfo")
        default_source = si.get("default")
        sources = si.get("dataSources", si.get("datasources"))
    if sources is not None or default_source is not None:
        info = _element(doc, "orm-datasource-info", {"default": default_source})
        for i, item in enumerate(_list(sources, "dataSources")):
            node = _map(item, f"dataSources[{i}]")
            _reject_unknown(node, {"name", "type", "driverClass", "url", "username", "userName", "password"}, f"dataSources[{i}]")
            ds = _element(doc, "orm-datasource", {"name": _require(node, "name", f"dataSources[{i}]")})
            _append_text(doc, ds, "name", _require(node, "type", f"dataSources[{i}]"))
            _append_text(doc, ds, "driver-class", node.get("driverClass"))
            _append_text(doc, ds, "url", node.get("url"))
            _append_text(doc, ds, "username", node.get("username", node.get("userName")))
            _append_text(doc, ds, "password", node.get("password"))
            info.appendChild(ds)
        el.appendChild(info)

    file_sections = [
        ("dataFiles", "orm-data-file-info", "orm-file"),
        ("relationFiles", "orm-relation-file-info", "orm-file"),
        ("viewFiles", "orm-view-file-info", "orm-file"),
        ("ruleFiles", "orm-rule-file-info", "orm-file"),
        ("serviceFiles", "orm-service-info", "orm-file"),
        ("apiFiles", "api-file-info", "api-file"),
        ("enumFiles", "enum-file-info", "enum-file"),
        # Current P3 mix config uses dedicated child names, not orm-file.
        ("systemFiles", "system-file-info", "system-file"),
        ("businessFiles", "business-file-info", "business-file"),
    ]
    for key, xml_name, child_name in file_sections:
        if key not in root:
            continue
        section = _element(doc, xml_name)
        for i, item in enumerate(_list(root.get(key), key)):
            path = item.get("path") if isinstance(item, Mapping) else item
            if isinstance(item, Mapping):
                _reject_unknown(item, {"path"}, f"{key}[{i}]")
            if path is None or path == "":
                raise ConversionError(f"{key}[{i}].path: required")
            section.appendChild(_element(doc, child_name, {"path": path}))
        el.appendChild(section)

    conn_info = root.get("connectionInfo")
    direct_connections = root.get("connections")
    default_conn = None
    connections: Any = direct_connections
    if conn_info is not None:
        ci = _map(conn_info, "connectionInfo")
        _reject_unknown(ci, {"default", "connections"}, "connectionInfo")
        default_conn = ci.get("default")
        connections = ci.get("connections")
    if connections is not None or default_conn is not None:
        info = _element(doc, "orm-connection-info", {"default": default_conn})
        for i, item in enumerate(_list(connections, "connections")):
            node = _map(item, f"connections[{i}]")
            _reject_unknown(node, {"name", "dataSources", "properties"}, f"connections[{i}]")
            con = _element(doc, "orm-connection", {"name": _require(node, "name", f"connections[{i}]")})
            refs = _element(doc, "data-source-info")
            for ref in _list(node.get("dataSources"), f"connections[{i}].dataSources"):
                if isinstance(ref, Mapping):
                    ref = ref.get("ref", ref.get("name"))
                if ref is None:
                    raise ConversionError(f"connections[{i}].dataSources: ref required")
                refs.appendChild(_element(doc, "data-source", {"ref": ref}))
            con.appendChild(refs)
            props = node.get("properties")
            if props:
                p_el = _element(doc, "property-info")
                for pk, pv in _map(props, f"connections[{i}].properties").items():
                    p_el.appendChild(_element(doc, "property", {"name": pk, "value": pv}))
                con.appendChild(p_el)
            info.appendChild(con)
        el.appendChild(info)
    return el


def _iter_named_mapping_or_list(value: Any, path: str) -> List[Tuple[str, Any]]:
    if isinstance(value, Mapping):
        return [(str(k), v) for k, v in value.items()]
    out: List[Tuple[str, Any]] = []
    for i, item in enumerate(_list(value, path)):
        node = _map(item, f"{path}[{i}]")
        name = _require(node, "name", f"{path}[{i}]")
        out.append((str(name), node))
    return out


def _build_data(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    top = _element(doc, "orm-data-mapping")
    for i, raw in enumerate(_list(root.get("datas"), "datas")):
        node = _map(raw, f"datas[{i}]")
        _reject_unknown(node, {"name", "system", "class", "className", "desc", "properties", "tables"}, f"datas[{i}]")
        _id_comment(doc, top, node, emit)
        attrs = {"name": _require(node, "name", f"datas[{i}]"), "system": _require(node, "system", f"datas[{i}]")}
        if node.get("class") or node.get("className"):
            attrs["class"] = node.get("class", node.get("className"))
        data_el = _element(doc, "data", attrs)
        props_el = _element(doc, "property-info")
        for name, raw_prop in _iter_named_mapping_or_list(node.get("properties", {}), f"datas[{i}].properties"):
            if isinstance(raw_prop, Mapping):
                prop = _map(raw_prop, f"datas[{i}].properties.{name}")
                _reject_unknown(prop, {"name", "type", "desc"}, f"datas[{i}].properties.{name}")
                p_attrs = {"name": name, "type": prop.get("type"), "desc": prop.get("desc")}
            else:
                p_attrs = {"name": name, "type": raw_prop}
            props_el.appendChild(_element(doc, "property", p_attrs))
        data_el.appendChild(props_el)

        tables_el = _element(doc, "table-info")
        for ti, raw_table in enumerate(_list(node.get("tables"), f"datas[{i}].tables")):
            table = _map(raw_table, f"datas[{i}].tables[{ti}]")
            _reject_unknown(table, {"name", "dataSource", "key", "keyType", "columns"}, f"datas[{i}].tables[{ti}]")
            table_el = _element(doc, "table", {
                "name": _require(table, "name", f"datas[{i}].tables[{ti}]"),
                "data-source": _require(table, "dataSource", f"datas[{i}].tables[{ti}]"),
                "key": table.get("key"),
                "key-type": table.get("keyType"),
            })
            for col_name, raw_col in _iter_named_mapping_or_list(table.get("columns", {}), f"datas[{i}].tables[{ti}].columns"):
                if isinstance(raw_col, Mapping):
                    col = _map(raw_col, f"column {col_name}")
                    _reject_unknown(col, {"name", "ref", "refProperty", "type", "relEnum"}, f"column {col_name}")
                    ref = col.get("ref", col.get("refProperty"))
                    if ref is None:
                        raise ConversionError(f"column {col_name}.ref: required")
                    attrs2 = {"name": col_name, "ref-property": ref, "type": col.get("type"), "rel-enum": col.get("relEnum")}
                else:
                    attrs2 = {"name": col_name, "ref-property": raw_col}
                table_el.appendChild(_element(doc, "column", attrs2))
            tables_el.appendChild(table_el)
        if node.get("tables") is not None:
            data_el.appendChild(tables_el)
        top.appendChild(data_el)
    return top


def _append_view_properties(doc: minidom.Document, parent: minidom.Element, value: Any, path: str) -> None:
    props_el = _element(doc, "property-info")
    for name, raw_prop in _iter_named_mapping_or_list(value or {}, path):
        if isinstance(raw_prop, Mapping):
            prop = _map(raw_prop, f"{path}.{name}")
            _reject_unknown(prop, {"name", "ref", "refProperty", "relation", "data", "key", "relKey", "relValue", "desc", "properties"}, f"{path}.{name}")
            if prop.get("relation"):
                attrs = {
                    "name": name,
                    "relation": prop.get("relation"),
                    "data": _require(prop, "data", f"{path}.{name}"),
                    "key": _require(prop, "key", f"{path}.{name}"),
                    "rel-key": _require(prop, "relKey", f"{path}.{name}"),
                    "rel-value": prop.get("relValue"),
                    "desc": prop.get("desc"),
                }
                p_el = _element(doc, "property", attrs)
                nested_holder = _element(doc, "_holder")
                _append_view_properties(doc, nested_holder, prop.get("properties", {}), f"{path}.{name}.properties")
                nested_prop_info = nested_holder.firstChild
                if nested_prop_info is not None:
                    while nested_prop_info.firstChild:
                        p_el.appendChild(nested_prop_info.firstChild)
                props_el.appendChild(p_el)
            else:
                ref = prop.get("ref", prop.get("refProperty"))
                if ref is None:
                    raise ConversionError(f"{path}.{name}.ref: required")
                props_el.appendChild(_element(doc, "property", {"name": name, "ref-property": ref, "rel-value": prop.get("relValue"), "desc": prop.get("desc")}))
        else:
            props_el.appendChild(_element(doc, "property", {"name": name, "ref-property": raw_prop}))
    parent.appendChild(props_el)


def _build_view(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    top = _element(doc, "orm-view-mapping")
    for i, raw in enumerate(_list(root.get("views"), "views")):
        node = _map(raw, f"views[{i}]")
        _reject_unknown(node, {"name", "system", "targetMain", "class", "className", "properties"}, f"views[{i}]")
        _id_comment(doc, top, node, emit)
        attrs = {
            "name": _require(node, "name", f"views[{i}]"),
            "system": _require(node, "system", f"views[{i}]"),
            "target-main": _require(node, "targetMain", f"views[{i}]"),
            "class": node.get("class", node.get("className")),
        }
        view_el = _element(doc, "view", attrs)
        _append_view_properties(doc, view_el, node.get("properties", {}), f"views[{i}].properties")
        top.appendChild(view_el)
    return top


def _build_rule(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    top = _element(doc, "orm-rule-mapping")
    rule_views = root.get("ruleViews", root.get("rule-view-info"))
    for i, raw in enumerate(_list(rule_views, "ruleViews")):
        node = _map(raw, f"ruleViews[{i}]")
        _reject_unknown(node, {"name", "code", "desc", "viewRef", "apiRef", "dataSource", "rules"}, f"ruleViews[{i}]")
        _id_comment(doc, top, node, emit)
        rv = _element(doc, "rule-view-info", {
            "name": _require(node, "name", f"ruleViews[{i}]"),
            "code": _require(node, "code", f"ruleViews[{i}]"),
            "desc": node.get("desc"),
            "view-ref": _require(node, "viewRef", f"ruleViews[{i}]"),
            "api-ref": node.get("apiRef"),
            "dataSource": node.get("dataSource"),
        })
        for ri, raw_rule in enumerate(_list(node.get("rules"), f"ruleViews[{i}].rules")):
            rule = _map(raw_rule, f"ruleViews[{i}].rules[{ri}]")
            _reject_unknown(rule, {"name", "type", "property", "pattern", "sql", "cmd", "process", "grammer", "dataSource", "error", "customer"}, f"ruleViews[{i}].rules[{ri}]")
            _id_comment(doc, rv, rule, emit)
            raw_type = _require(rule, "type", f"ruleViews[{i}].rules[{ri}]")
            xml_type = "grammer" if raw_type == "dsl" else raw_type
            if rule.get("cmd") is not None and rule.get("sql") is not None and rule.get("cmd") != rule.get("sql"):
                raise ConversionError(f"ruleViews[{i}].rules[{ri}]: cmd and sql conflict")
            r_el = _element(doc, "rule", {
                "name": _require(rule, "name", f"ruleViews[{i}].rules[{ri}]"),
                "type": xml_type,
                "property": rule.get("property"),
                "pattern": rule.get("pattern"),
                "sql": rule.get("cmd", rule.get("sql")),
                "dataSource": rule.get("dataSource"),
            })
            if rule.get("error") is not None:
                err = _map(rule["error"], f"ruleViews[{i}].rules[{ri}].error")
                _reject_unknown(err, {"code", "message", "level"}, f"ruleViews[{i}].rules[{ri}].error")
                r_el.appendChild(_element(doc, "error-info", {"code": err.get("code"), "message": err.get("message"), "level": err.get("level")}))
            if rule.get("customer") is not None:
                cust = _map(rule["customer"], f"ruleViews[{i}].rules[{ri}].customer")
                r_el.appendChild(_element(doc, "customer-info", cust))
            process = rule.get("process", rule.get("grammer"))
            if process is not None:
                _append_cdata(doc, r_el, "customer-process", process)
            rv.appendChild(r_el)
        top.appendChild(rv)
    return top




def _build_enum(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    top = _element(doc, "enum-config")
    items = _list(root.get("enums"), "enums")
    if not items:
        raise ConversionError("enums: at least one enum is required")
    for i, raw in enumerate(items):
        node = _map(raw, f"enums[{i}]")
        _reject_unknown(node, {"name", "desc", "values"}, f"enums[{i}]")
        _id_comment(doc, top, node, emit)
        enum_el = _element(doc, "enum", {
            "name": _require(node, "name", f"enums[{i}]"),
            "desc": node.get("desc"),
        })
        values = _list(node.get("values"), f"enums[{i}].values")
        if not values:
            raise ConversionError(f"enums[{i}].values: at least one enum value is required")
        for vi, raw_value in enumerate(values):
            value = _map(raw_value, f"enums[{i}].values[{vi}]")
            _reject_unknown(value, {"value", "name", "desc"}, f"enums[{i}].values[{vi}]")
            _id_comment(doc, enum_el, value, emit)
            enum_el.appendChild(_element(doc, "enum-value", {
                "value": _require(value, "value", f"enums[{i}].values[{vi}]"),
                "name": _require(value, "name", f"enums[{i}].values[{vi}]"),
                "desc": value.get("desc"),
            }))
        top.appendChild(enum_el)
    return top


def _append_api_validations(doc: minidom.Document, parent: minidom.Element, values: Any, path: str) -> None:
    validation_types = {"notNull", "notEmpty", "min", "max", "minLength", "maxLength", "pattern", "regex", "enum", "expression"}
    vals = _list(values, path)
    if not vals:
        return
    v_info = _element(doc, "validation-info")
    for vi, raw_val in enumerate(vals):
        val = _map(raw_val, f"{path}[{vi}]")
        _reject_unknown(val, {"type", "value", "values", "expression", "message"}, f"{path}[{vi}]")
        vtype = _require(val, "type", f"{path}[{vi}]")
        if vtype not in validation_types:
            raise ConversionError(f"{path}[{vi}].type: unsupported type {vtype!r}")
        if vtype == "expression" and not val.get("expression"):
            raise ConversionError(f"{path}[{vi}].expression: required for expression")
        v_el = _element(doc, "validation", {
            "type": vtype,
            "value": val.get("value"),
            "expression": val.get("expression"),
            "message": val.get("message"),
        })
        for item in _list(val.get("values"), f"{path}[{vi}].values"):
            _append_text(doc, v_el, "value", item)
        v_info.appendChild(v_el)
    parent.appendChild(v_info)


def _build_api(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    top = _element(doc, "api-config")
    api_items = _list(root.get("apis"), "apis")
    if not api_items:
        raise ConversionError("apis: at least one API is required")
    methods = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
    param_locations = {"path", "query", "header", "body"}
    for i, raw in enumerate(api_items):
        node = _map(raw, f"apis[{i}]")
        _reject_unknown(node, {"name", "desc", "system", "url", "method", "request", "response"}, f"apis[{i}]")
        method = _text(_require(node, "method", f"apis[{i}]")).upper()
        if method not in methods:
            raise ConversionError(f"apis[{i}].method: unsupported method {method!r}")
        _id_comment(doc, top, node, emit)
        api = _element(doc, "api", {
            "name": _require(node, "name", f"apis[{i}]"),
            "desc": node.get("desc"),
            "system": _require(node, "system", f"apis[{i}]"),
            "url": _require(node, "url", f"apis[{i}]"),
            "method": method,
        })
        request = node.get("request")
        if request is not None:
            req = _map(request, f"apis[{i}].request")
            _reject_unknown(req, {"params", "validations"}, f"apis[{i}].request")
            req_el = _element(doc, "request")
            _append_api_validations(doc, req_el, req.get("validations"), f"apis[{i}].request.validations")
            for pi, raw_param in enumerate(_list(req.get("params"), f"apis[{i}].request.params")):
                param = _map(raw_param, f"apis[{i}].request.params[{pi}]")
                _reject_unknown(param, {"name", "in", "type", "required", "desc", "default", "relEnum", "validations"}, f"apis[{i}].request.params[{pi}]")
                loc = _require(param, "in", f"apis[{i}].request.params[{pi}]")
                if loc not in param_locations:
                    raise ConversionError(f"apis[{i}].request.params[{pi}].in: unsupported location {loc!r}")
                _id_comment(doc, req_el, param, emit)
                p_el = _element(doc, "parameter", {
                    "name": _require(param, "name", f"apis[{i}].request.params[{pi}]"),
                    "in": loc,
                    "type": _require(param, "type", f"apis[{i}].request.params[{pi}]"),
                    "required": param.get("required"),
                    "desc": param.get("desc"),
                    "default": param.get("default"),
                    "rel-enum": param.get("relEnum"),
                })
                _append_api_validations(doc, p_el, param.get("validations"), f"apis[{i}].request.params[{pi}].validations")
                req_el.appendChild(p_el)
            api.appendChild(req_el)
        response = node.get("response")
        if response is not None:
            res = _map(response, f"apis[{i}].response")
            _reject_unknown(res, {"type", "desc", "modelRef", "fields"}, f"apis[{i}].response")
            res_el = _element(doc, "response", {
                "type": _require(res, "type", f"apis[{i}].response"),
                "desc": res.get("desc"),
                "model-ref": res.get("modelRef"),
            })
            for fi, raw_field in enumerate(_list(res.get("fields"), f"apis[{i}].response.fields")):
                field = _map(raw_field, f"apis[{i}].response.fields[{fi}]")
                _reject_unknown(field, {"name", "type", "required", "desc", "relEnum", "validations"}, f"apis[{i}].response.fields[{fi}]")
                _id_comment(doc, res_el, field, emit)
                field_el = _element(doc, "field", {
                    "name": _require(field, "name", f"apis[{i}].response.fields[{fi}]"),
                    "type": _require(field, "type", f"apis[{i}].response.fields[{fi}]"),
                    "required": field.get("required"),
                    "desc": field.get("desc"),
                    "rel-enum": field.get("relEnum"),
                })
                _append_api_validations(doc, field_el, field.get("validations"), f"apis[{i}].response.fields[{fi}].validations")
                res_el.appendChild(field_el)
            api.appendChild(res_el)
        top.appendChild(api)
    return top

def _append_actions(doc: minidom.Document, parent: minidom.Element, actions: Any, path: str, emit: bool) -> None:
    items = _list(actions, path)
    if not items:
        return
    info = _element(doc, "action-info")
    for i, raw in enumerate(items):
        node = _map(raw, f"{path}[{i}]")
        _reject_unknown(node, {"name", "systemRef", "ruleRef", "produces", "rules"}, f"{path}[{i}]")
        _id_comment(doc, info, node, emit)
        action = _element(doc, "action", {
            "name": _require(node, "name", f"{path}[{i}]"),
            "system-ref": node.get("systemRef"),
            "rule-ref": node.get("ruleRef"),
        })
        for rj, raw_rule in enumerate(_list(node.get("rules"), f"{path}[{i}].rules")):
            rule = _map(raw_rule, f"{path}[{i}].rules[{rj}]")
            _reject_unknown(rule, {"name", "type", "property", "pattern", "sql", "cmd"}, f"{path}[{i}].rules[{rj}]")
            raw_type = _require(rule, "type", f"{path}[{i}].rules[{rj}]")
            xml_type = "grammer" if raw_type == "dsl" else raw_type
            if rule.get("cmd") is not None and rule.get("sql") is not None and rule.get("cmd") != rule.get("sql"):
                raise ConversionError(f"{path}[{i}].rules[{rj}]: cmd and sql conflict")
            action.appendChild(_element(doc, "rule", {
                "name": _require(rule, "name", f"{path}[{i}].rules[{rj}]"),
                "type": xml_type,
                "property": rule.get("property"), "pattern": rule.get("pattern"), "sql": rule.get("cmd", rule.get("sql")),
            }))
        produces = _list(node.get("produces"), f"{path}[{i}].produces")
        if produces:
            p_info = _element(doc, "produce-info")
            for pj, raw_prod in enumerate(produces):
                prod = _map(raw_prod, f"{path}[{i}].produces[{pj}]")
                _reject_unknown(prod, {"ref", "informationRef"}, f"{path}[{i}].produces[{pj}]")
                _id_comment(doc, p_info, prod, emit)
                p_info.appendChild(_element(doc, "produce", {"ref": _require(prod, "ref", f"{path}[{i}].produces[{pj}]"), "information-ref": prod.get("informationRef")}))
            action.appendChild(p_info)
        info.appendChild(action)
    parent.appendChild(info)


def _append_change(doc: minidom.Document, parent: minidom.Element, change: Any, path: str) -> None:
    if change is None:
        return
    if isinstance(change, Mapping):
        node = _map(change, path)
        _reject_unknown(node, {"informationRef", "process"}, path)
        if node.get("informationRef") is not None:
            parent.appendChild(_element(doc, "change-info", {"information-ref": node.get("informationRef")}))
        elif node.get("process") is not None:
            _append_cdata(doc, parent, "change-info", node.get("process"))
        else:
            raise ConversionError(f"{path}: informationRef or process required")
    else:
        _append_cdata(doc, parent, "change-info", change)


def _build_systems(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    top = _element(doc, "systems")
    for i, raw in enumerate(_list(root.get("systems"), "systems")):
        node = _map(raw, f"systems[{i}]")
        _reject_unknown(node, {"name", "desc", "dataRefs", "viewRefs", "ruleFiles", "information", "modelAccess"}, f"systems[{i}]")
        _id_comment(doc, top, node, emit)
        s = _element(doc, "system", {"name": _require(node, "name", f"systems[{i}]")})
        if node.get("dataRefs") is not None:
            info = _element(doc, "data-info")
            for ref in _list(node.get("dataRefs"), f"systems[{i}].dataRefs"):
                name = ref.get("name") if isinstance(ref, Mapping) else ref
                info.appendChild(_element(doc, "data-ref", {"name": name}))
            s.appendChild(info)
        if node.get("viewRefs") is not None:
            info = _element(doc, "view-info")
            for ref in _list(node.get("viewRefs"), f"systems[{i}].viewRefs"):
                name = ref.get("name") if isinstance(ref, Mapping) else ref
                info.appendChild(_element(doc, "view-ref", {"name": name}))
            s.appendChild(info)
        if node.get("ruleFiles") is not None:
            info = _element(doc, "rule-file-info")
            for ref in _list(node.get("ruleFiles"), f"systems[{i}].ruleFiles"):
                path = ref.get("path") if isinstance(ref, Mapping) else ref
                info.appendChild(_element(doc, "rule-file", {"path": path}))
            s.appendChild(info)
        infos = _list(node.get("information"), f"systems[{i}].information")
        if infos:
            info_el = _element(doc, "information-info")
            for ii, raw_info in enumerate(infos):
                inf = _map(raw_info, f"systems[{i}].information[{ii}]")
                _reject_unknown(inf, {"name", "viewRef", "ruleRef", "ruleData", "expression", "changeData"}, f"systems[{i}].information[{ii}]")
                _id_comment(doc, info_el, inf, emit)
                i_el = _element(doc, "information", {
                    "name": _require(inf, "name", f"systems[{i}].information[{ii}]"),
                    "view-ref": inf.get("viewRef"),
                    "rule-ref": inf.get("ruleRef"),
                    "rule-data": inf.get("ruleData"),
                    "expression": inf.get("expression"),
                })
                if inf.get("changeData") is not None:
                    _append_cdata(doc, i_el, "change-data", inf.get("changeData"))
                info_el.appendChild(i_el)
            s.appendChild(info_el)
        accesses = _list(node.get("modelAccess"), f"systems[{i}].modelAccess")
        if accesses:
            ma_info = _element(doc, "model-access-info")
            for mi, raw_ma in enumerate(accesses):
                ma = _map(raw_ma, f"systems[{i}].modelAccess[{mi}]")
                _reject_unknown(ma, {"modelRef", "read", "write"}, f"systems[{i}].modelAccess[{mi}]")
                ma_el = _element(doc, "model-access", {"model-ref": _require(ma, "modelRef", f"systems[{i}].modelAccess[{mi}]")})
                for mode in ("read", "write"):
                    for ai, raw_access in enumerate(_list(ma.get(mode), f"systems[{i}].modelAccess[{mi}].{mode}")):
                        access = _map(raw_access, f"systems[{i}].modelAccess[{mi}].{mode}[{ai}]")
                        _reject_unknown(access, {"path", "ref", "refs"}, f"systems[{i}].modelAccess[{mi}].{mode}[{ai}]")
                        a_el = _element(doc, mode, {"path": _require(access, "path", f"systems[{i}].modelAccess[{mi}].{mode}[{ai}]")})
                        refs = access.get("refs") if access.get("refs") is not None else access.get("ref")
                        for rj, raw_ref in enumerate(_list(refs, "ref")):
                            ref = _map(raw_ref, f"ref[{rj}]")
                            _reject_unknown(ref, {"view", "property"}, f"ref[{rj}]")
                            a_el.appendChild(_element(doc, "ref", {"view": _require(ref, "view", f"ref[{rj}]"), "property": _require(ref, "property", f"ref[{rj}]")}))
                        ma_el.appendChild(a_el)
                ma_info.appendChild(ma_el)
            s.appendChild(ma_info)
        top.appendChild(s)
    return top


def _append_business_subdirectories(doc: minidom.Document, parent: minidom.Element, subs: Any, path: str, emit: bool) -> None:
    items = _list(subs, path)
    if not items:
        return
    info = _element(doc, "subdirectory-info")
    for i, raw in enumerate(items):
        node = _map(raw, f"{path}[{i}]")
        _reject_unknown(node, {"rel", "role", "informationRef", "mutualExclusion", "anyOne", "back"}, f"{path}[{i}]")
        sub = _element(doc, "subdirectory", {
            "rel": _require(node, "rel", f"{path}[{i}]"),
            "role": node.get("role"),
            "information-ref": node.get("informationRef"),
            "mutual-exclusion": node.get("mutualExclusion"),
            "any-one": node.get("anyOne"),
        })
        if node.get("back") is not None:
            back = _map(node["back"], f"{path}[{i}].back")
            _reject_unknown(back, {"name", "actions"}, f"{path}[{i}].back")
            b_el = _element(doc, "back", {"name": _require(back, "name", f"{path}[{i}].back")})
            _append_actions(doc, b_el, back.get("actions"), f"{path}[{i}].back.actions", emit)
            sub.appendChild(b_el)
        info.appendChild(sub)
    parent.appendChild(info)


def _build_business(doc: minidom.Document, root: Mapping[str, Any], emit: bool) -> minidom.Element:
    b = _map(root.get("business"), "business")
    _reject_unknown(b, {"name", "desc", "directories"}, "business")
    top = _element(doc, "business-config", {"name": _require(b, "name", "business"), "desc": b.get("desc")})
    # Business is the XML root, so its stable design ID is emitted as the first
    # child comment when tracing is enabled. This allows XML -> YAML migration
    # to restore the canonical business ID without changing Runtime semantics.
    _id_comment(doc, top, b, emit)
    dirs_info = _element(doc, "directory-info")
    for i, raw in enumerate(_list(b.get("directories"), "business.directories")):
        node = _map(raw, f"business.directories[{i}]")
        _reject_unknown(node, {"name", "type", "informationRef", "modelRef", "isRoot", "subDirectories", "dependencies", "actions", "change"}, f"business.directories[{i}]")
        _id_comment(doc, dirs_info, node, emit)
        d = _element(doc, "directory", {
            "name": _require(node, "name", f"business.directories[{i}]"),
            "type": node.get("type"),
            "information-ref": node.get("informationRef"),
            "model-ref": node.get("modelRef"),
            "is-root": node.get("isRoot"),
        })
        _append_business_subdirectories(doc, d, node.get("subDirectories"), f"business.directories[{i}].subDirectories", emit)
        deps = _list(node.get("dependencies"), f"business.directories[{i}].dependencies")
        if deps:
            dep_info = _element(doc, "dependency-info")
            for di, raw_dep in enumerate(deps):
                dep = _map(raw_dep, f"business.directories[{i}].dependencies[{di}]")
                _reject_unknown(dep, {"informationRef"}, f"business.directories[{i}].dependencies[{di}]")
                dep_info.appendChild(_element(doc, "dependency", {"information-ref": _require(dep, "informationRef", f"business.directories[{i}].dependencies[{di}]")}))
            d.appendChild(dep_info)
        _append_actions(doc, d, node.get("actions"), f"business.directories[{i}].actions", emit)
        _append_change(doc, d, node.get("change"), f"business.directories[{i}].change")
        dirs_info.appendChild(d)
    top.appendChild(dirs_info)
    return top


def load_yaml(path: Path) -> Dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as fh:
            data = yaml.safe_load(fh)
    except yaml.YAMLError as exc:
        raise ConversionError(f"{path}: YAML parse error: {exc}") from exc
    if data is None:
        raise ConversionError(f"{path}: empty YAML")
    if not isinstance(data, Mapping):
        raise ConversionError(f"{path}: root must be mapping")
    return dict(data)


def convert_file(src: Path, dst: Optional[Path], *, check: bool, emit_id_comments: bool, stdout: bool) -> None:
    xml = convert_document(load_yaml(src), emit_id_comments=emit_id_comments)
    if stdout:
        sys.stdout.write(xml)
    if not check and dst is not None:
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(xml, encoding="utf-8")


def _yaml_files(path: Path) -> List[Path]:
    return sorted([p for p in path.rglob("*") if p.is_file() and p.suffix.lower() in {".yaml", ".yml"}])


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Convert canonical DEC YAML to DEC XML")
    parser.add_argument("input", type=Path, help="YAML file or directory")
    parser.add_argument("-o", "--output", type=Path, help="XML output file, or output directory for directory input")
    parser.add_argument("--check", action="store_true", help="validate and convert in memory without writing")
    parser.add_argument("--stdout", action="store_true", help="write generated XML to stdout (file input only)")
    parser.add_argument("--emit-id-comments", action="store_true", help="emit stable YAML design IDs as XML comments")
    args = parser.parse_args(argv)

    try:
        src = args.input.resolve()
        if not src.exists():
            raise ConversionError(f"input does not exist: {src}")
        if src.is_file():
            if args.output is None and not args.check and not args.stdout:
                raise ConversionError("file input requires --output, --stdout, or --check")
            convert_file(src, args.output.resolve() if args.output else None, check=args.check, emit_id_comments=args.emit_id_comments, stdout=args.stdout)
            if args.check and not args.stdout:
                print(f"OK {src}")
            return 0

        if args.stdout:
            raise ConversionError("--stdout cannot be used with directory input")
        files = _yaml_files(src)
        if not files:
            raise ConversionError(f"no .yaml/.yml files under {src}")
        out_dir = args.output.resolve() if args.output else None
        if not args.check and out_dir is None:
            raise ConversionError("directory input requires --output directory or --check")
        errors: List[str] = []
        for file in files:
            rel = file.relative_to(src).with_suffix(".xml")
            dst = out_dir / rel if out_dir else None
            try:
                convert_file(file, dst, check=args.check, emit_id_comments=args.emit_id_comments, stdout=False)
                print(f"OK {file}")
            except ConversionError as exc:
                errors.append(str(exc))
                print(f"ERROR {exc}", file=sys.stderr)
        return 1 if errors else 0
    except ConversionError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
