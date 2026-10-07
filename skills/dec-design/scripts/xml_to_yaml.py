#!/usr/bin/env python3
"""DEC XML -> canonical DEC YAML converter.

This tool is primarily for migration/import of existing DEC XML. Canonical YAML
remains the source of truth after conversion.

Design IDs are restored from ``<!-- dec-id: ... -->`` comments when present.
With the default ``--id-mode auto``, deterministic IDs are synthesized when XML
contains no DEC ID comment.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple
from xml.dom import Node, minidom

import yaml


class ConversionError(ValueError):
    pass


DEC_VERSION = "dec/v1"
ID_COMMENT_RE = re.compile(r"^\s*dec-id\s*:\s*(\S+)\s*$")
SUPPORTED_ROOTS = {
    "orm-config": "config",
    "orm-data-mapping": "data",
    "orm--data-mapping": "data",  # historical typo accepted on import only
    "orm-view-mapping": "view",
    "orm-rule-mapping": "rule",
    "api-config": "api",
    "enum-config": "enum",
    "systems": "systems",
    "business-config": "business",
}


class LiteralDumper(yaml.SafeDumper):
    pass


def _str_representer(dumper: yaml.Dumper, value: str):
    style = "|" if "\n" in value else None
    return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)


LiteralDumper.add_representer(str, _str_representer)


def _elements(parent: minidom.Element, name: Optional[str] = None) -> List[minidom.Element]:
    out: List[minidom.Element] = []
    for child in parent.childNodes:
        if child.nodeType == Node.ELEMENT_NODE and (name is None or child.tagName == name):
            out.append(child)
    return out


def _first(parent: minidom.Element, name: str) -> Optional[minidom.Element]:
    for child in parent.childNodes:
        if child.nodeType == Node.ELEMENT_NODE and child.tagName == name:
            return child
    return None


def _text_content(el: minidom.Element) -> str:
    parts: List[str] = []
    for child in el.childNodes:
        if child.nodeType in {Node.TEXT_NODE, Node.CDATA_SECTION_NODE}:
            parts.append(child.data)
    return "".join(parts).strip("\n")


def _attr(el: minidom.Element, name: str) -> Optional[str]:
    if not el.hasAttribute(name):
        return None
    return el.getAttribute(name)


def _required_attr(el: minidom.Element, name: str, path: str) -> str:
    value = _attr(el, name)
    if value is None or value == "":
        raise ConversionError(f"{path}@{name}: required")
    return value


def _bool(value: Optional[str]) -> Optional[bool]:
    if value is None:
        return None
    low = value.strip().lower()
    if low == "true":
        return True
    if low == "false":
        return False
    raise ConversionError(f"expected boolean true/false, got {value!r}")


def _number_or_text(value: Optional[str]) -> Any:
    if value is None:
        return None
    s = value.strip()
    if re.fullmatch(r"[-+]?\d+", s):
        try:
            return int(s)
        except ValueError:
            return s
    if re.fullmatch(r"[-+]?(?:\d+\.\d*|\d*\.\d+)", s):
        try:
            return float(s)
        except ValueError:
            return s
    return value


def _assert_attrs(el: minidom.Element, allowed: Iterable[str], path: str) -> None:
    allowed_set = set(allowed)
    unknown = sorted(attr.name for attr in el.attributes.values() if attr.name not in allowed_set)
    if unknown:
        raise ConversionError(f"{path}: unsupported attributes: {', '.join(unknown)}")


def _assert_children(el: minidom.Element, allowed: Iterable[str], path: str) -> None:
    allowed_set = set(allowed)
    unknown = sorted({child.tagName for child in _elements(el) if child.tagName not in allowed_set})
    if unknown:
        raise ConversionError(f"{path}: unsupported child elements: {', '.join(unknown)}")


def _comment_id_before(parent: minidom.Element, target: minidom.Element) -> Optional[str]:
    pending: Optional[str] = None
    for child in parent.childNodes:
        if child.nodeType == Node.COMMENT_NODE:
            match = ID_COMMENT_RE.match(child.data)
            pending = match.group(1) if match else None
        elif child.nodeType == Node.ELEMENT_NODE:
            if child is target:
                return pending
            pending = None
        elif child.nodeType == Node.TEXT_NODE and child.data.strip() == "":
            continue
        else:
            pending = None
    return None


def _leading_comment_id(parent: minidom.Element) -> Optional[str]:
    for child in parent.childNodes:
        if child.nodeType == Node.COMMENT_NODE:
            match = ID_COMMENT_RE.match(child.data)
            if match:
                return match.group(1)
        elif child.nodeType == Node.TEXT_NODE and child.data.strip() == "":
            continue
        elif child.nodeType == Node.ELEMENT_NODE:
            break
    return None


def _slug(value: str) -> str:
    s = re.sub(r"[^A-Za-z0-9]+", "-", value.strip()).strip("-").upper()
    return s or "UNNAMED"


class IdFactory:
    def __init__(self, mode: str):
        self.mode = mode
        self.used: set[str] = set()

    def _claim(self, candidate: str) -> str:
        if candidate not in self.used:
            self.used.add(candidate)
            return candidate
        n = 2
        while f"{candidate}-{n}" in self.used:
            n += 1
        result = f"{candidate}-{n}"
        self.used.add(result)
        return result

    def resolve(self, parent: Optional[minidom.Element], el: minidom.Element, generated: str, *, root_leading: bool = False) -> Optional[str]:
        comment_id: Optional[str] = None
        if root_leading:
            comment_id = _leading_comment_id(el)
        elif parent is not None:
            comment_id = _comment_id_before(parent, el)
        if comment_id:
            return self._claim(comment_id)
        if self.mode == "none" or self.mode == "comments":
            return None
        return self._claim(generated)


def _with_id(node: Dict[str, Any], design_id: Optional[str]) -> Dict[str, Any]:
    if design_id:
        return {"id": design_id, **node}
    return node


def _yaml_document(kind: str, body: Mapping[str, Any]) -> Dict[str, Any]:
    return {"kind": kind, "version": DEC_VERSION, **body}


def _parse_config(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), "orm-config")
    _assert_children(root, {
        "orm-datasource-info", "orm-data-file-info", "orm-relation-file-info", "orm-view-file-info",
        "orm-rule-file-info", "orm-service-info", "api-file-info", "enum-file-info", "system-file-info",
        "business-file-info", "orm-connection-info",
    }, "orm-config")
    out: Dict[str, Any] = {}
    ds_info = _first(root, "orm-datasource-info")
    if ds_info is not None:
        _assert_attrs(ds_info, {"default"}, "orm-datasource-info")
        _assert_children(ds_info, {"orm-datasource"}, "orm-datasource-info")
        data_sources: List[Dict[str, Any]] = []
        for i, ds in enumerate(_elements(ds_info, "orm-datasource")):
            path = f"orm-datasource-info/orm-datasource[{i}]"
            _assert_attrs(ds, {"name"}, path)
            _assert_children(ds, {"name", "driver-class", "url", "username", "password"}, path)
            item: Dict[str, Any] = {
                "name": _required_attr(ds, "name", path),
                "type": _text_content(_first(ds, "name")) if _first(ds, "name") else None,
            }
            if not item["type"]:
                raise ConversionError(f"{path}/name: required")
            mapping = {
                "driver-class": "driverClass", "url": "url", "username": "username", "password": "password"
            }
            for xml_name, yaml_name in mapping.items():
                child = _first(ds, xml_name)
                if child is not None:
                    item[yaml_name] = _text_content(child)
            data_sources.append(item)
        info: Dict[str, Any] = {"dataSources": data_sources}
        default = _attr(ds_info, "default")
        if default is not None:
            info["default"] = default
            info = {"default": default, "dataSources": data_sources}
        out["dataSourceInfo"] = info

    file_sections = [
        ("orm-data-file-info", "orm-file", "dataFiles"),
        ("orm-relation-file-info", "orm-file", "relationFiles"),
        ("orm-view-file-info", "orm-file", "viewFiles"),
        ("orm-rule-file-info", "orm-file", "ruleFiles"),
        ("orm-service-info", "orm-file", "serviceFiles"),
        ("api-file-info", "api-file", "apiFiles"),
        ("enum-file-info", "enum-file", "enumFiles"),
        ("system-file-info", "system-file", "systemFiles"),
        ("business-file-info", "business-file", "businessFiles"),
    ]
    for section_name, child_name, yaml_name in file_sections:
        section = _first(root, section_name)
        if section is None:
            continue
        _assert_attrs(section, set(), section_name)
        _assert_children(section, {child_name}, section_name)
        values: List[Dict[str, str]] = []
        for i, child in enumerate(_elements(section, child_name)):
            _assert_attrs(child, {"path"}, f"{section_name}/{child_name}[{i}]")
            values.append({"path": _required_attr(child, "path", f"{section_name}/{child_name}[{i}]")})
        out[yaml_name] = values

    conn_info = _first(root, "orm-connection-info")
    if conn_info is not None:
        _assert_attrs(conn_info, {"default"}, "orm-connection-info")
        _assert_children(conn_info, {"orm-connection"}, "orm-connection-info")
        connections: List[Dict[str, Any]] = []
        for i, con in enumerate(_elements(conn_info, "orm-connection")):
            path = f"orm-connection-info/orm-connection[{i}]"
            _assert_attrs(con, {"name"}, path)
            _assert_children(con, {"data-source-info", "property-info"}, path)
            item: Dict[str, Any] = {"name": _required_attr(con, "name", path)}
            refs_el = _first(con, "data-source-info")
            if refs_el is None:
                raise ConversionError(f"{path}/data-source-info: required")
            refs = []
            for ri, ref in enumerate(_elements(refs_el, "data-source")):
                _assert_attrs(ref, {"ref"}, f"{path}/data-source-info/data-source[{ri}]")
                refs.append(_required_attr(ref, "ref", f"{path}/data-source-info/data-source[{ri}]"))
            item["dataSources"] = refs
            props_el = _first(con, "property-info")
            if props_el is not None:
                props: Dict[str, Any] = {}
                for pi, prop in enumerate(_elements(props_el, "property")):
                    _assert_attrs(prop, {"name", "value"}, f"{path}/property-info/property[{pi}]")
                    props[_required_attr(prop, "name", path)] = _required_attr(prop, "value", path)
                if props:
                    item["properties"] = props
            connections.append(item)
        info2: Dict[str, Any] = {"connections": connections}
        default = _attr(conn_info, "default")
        if default is not None:
            info2 = {"default": default, "connections": connections}
        out["connectionInfo"] = info2
    return _yaml_document("config", out)


def _parse_data(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), root.tagName)
    _assert_children(root, {"data"}, root.tagName)
    datas: List[Dict[str, Any]] = []
    for i, data in enumerate(_elements(root, "data")):
        path = f"data[{i}]"
        _assert_attrs(data, {"name", "system", "class"}, path)
        _assert_children(data, {"property-info", "table-info"}, path)
        name = _required_attr(data, "name", path)
        node: Dict[str, Any] = {"name": name}
        system = _attr(data, "system")
        if system is not None:
            node["system"] = system
        class_name = _attr(data, "class")
        if class_name is not None:
            node["className"] = class_name
        prop_info = _first(data, "property-info")
        if prop_info is None:
            raise ConversionError(f"{path}/property-info: required")
        properties: Dict[str, Any] = {}
        for pi, prop in enumerate(_elements(prop_info, "property")):
            ppath = f"{path}/property[{pi}]"
            _assert_attrs(prop, {"name", "type", "desc"}, ppath)
            pname = _required_attr(prop, "name", ppath)
            ptype = _required_attr(prop, "type", ppath)
            desc = _attr(prop, "desc")
            properties[pname] = {"type": ptype, "desc": desc} if desc is not None else ptype
        node["properties"] = properties
        table_info = _first(data, "table-info")
        if table_info is not None:
            tables: List[Dict[str, Any]] = []
            for ti, table in enumerate(_elements(table_info, "table")):
                tpath = f"{path}/table[{ti}]"
                _assert_attrs(table, {"name", "data-source", "key", "key-type"}, tpath)
                _assert_children(table, {"column"}, tpath)
                t: Dict[str, Any] = {
                    "name": _required_attr(table, "name", tpath),
                    "dataSource": _required_attr(table, "data-source", tpath),
                    "key": _required_attr(table, "key", tpath),
                    "keyType": _required_attr(table, "key-type", tpath),
                }
                cols: Dict[str, Any] = {}
                for ci, col in enumerate(_elements(table, "column")):
                    cpath = f"{tpath}/column[{ci}]"
                    _assert_attrs(col, {"name", "ref-property", "type", "rel-enum"}, cpath)
                    cname = _required_attr(col, "name", cpath)
                    ref = _required_attr(col, "ref-property", cpath)
                    ctype = _attr(col, "type")
                    rel_enum = _attr(col, "rel-enum")
                    if ctype is not None or rel_enum is not None:
                        cnode: Dict[str, Any] = {"ref": ref}
                        if ctype is not None:
                            cnode["type"] = ctype
                        if rel_enum is not None:
                            cnode["relEnum"] = rel_enum
                        cols[cname] = cnode
                    else:
                        cols[cname] = ref
                t["columns"] = cols
                tables.append(t)
            node["tables"] = tables
        design_id = ids.resolve(root, data, f"DATA-{_slug(name)}")
        datas.append(_with_id(node, design_id))
    return _yaml_document("data", {"datas": datas})


def _parse_view_properties(parent: minidom.Element, path: str) -> Dict[str, Any]:
    out: Dict[str, Any] = {}
    for i, prop in enumerate(_elements(parent, "property")):
        ppath = f"{path}/property[{i}]"
        _assert_attrs(prop, {"name", "ref-property", "relation", "data", "key", "rel-key", "rel-value", "desc"}, ppath)
        _assert_children(prop, {"property"}, ppath)
        name = _required_attr(prop, "name", ppath)
        relation = _attr(prop, "relation")
        if relation is not None:
            node: Dict[str, Any] = {
                "relation": relation,
                "data": _required_attr(prop, "data", ppath),
                "key": _required_attr(prop, "key", ppath),
                "relKey": _required_attr(prop, "rel-key", ppath),
            }
            rel_value = _attr(prop, "rel-value")
            if rel_value is not None:
                node["relValue"] = rel_value
            desc = _attr(prop, "desc")
            if desc is not None:
                node["desc"] = desc
            node["properties"] = _parse_view_properties(prop, f"{ppath}/properties")
            out[name] = node
        else:
            ref = _required_attr(prop, "ref-property", ppath)
            rel_value = _attr(prop, "rel-value")
            desc = _attr(prop, "desc")
            if rel_value is not None or desc is not None:
                node = {"ref": ref}
                if rel_value is not None: node["relValue"] = rel_value
                if desc is not None: node["desc"] = desc
                out[name] = node
            else:
                out[name] = ref
    return out


def _parse_view(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), "orm-view-mapping")
    _assert_children(root, {"view"}, "orm-view-mapping")
    views: List[Dict[str, Any]] = []
    for i, view in enumerate(_elements(root, "view")):
        path = f"view[{i}]"
        _assert_attrs(view, {"name", "system", "target-main", "class"}, path)
        _assert_children(view, {"property-info"}, path)
        name = _required_attr(view, "name", path)
        node: Dict[str, Any] = {
            "name": name,
            "system": _required_attr(view, "system", path),
            "targetMain": _required_attr(view, "target-main", path),
        }
        class_name = _attr(view, "class")
        if class_name is not None:
            node["className"] = class_name
        pinfo = _first(view, "property-info")
        if pinfo is None:
            raise ConversionError(f"{path}/property-info: required")
        node["properties"] = _parse_view_properties(pinfo, f"{path}/property-info")
        views.append(_with_id(node, ids.resolve(root, view, f"VIEW-{_slug(name)}")))
    return _yaml_document("view", {"views": views})


def _parse_rule_element(rule: minidom.Element, ids: IdFactory, parent: minidom.Element, context: str, path: str) -> Dict[str, Any]:
    _assert_attrs(rule, {"name", "type", "property", "pattern", "sql", "dataSource"}, path)
    _assert_children(rule, {"error-info", "customer-info", "customer-process"}, path)
    name = _required_attr(rule, "name", path)
    raw_type = _required_attr(rule, "type", path)
    node: Dict[str, Any] = {"name": name, "type": "dsl" if raw_type == "grammer" else raw_type}
    for xml_name, yaml_name in (("property", "property"), ("pattern", "pattern"), ("sql", "cmd"), ("dataSource", "dataSource")):
        value = _attr(rule, xml_name)
        if value is not None:
            node[yaml_name] = value
    err = _first(rule, "error-info")
    if err is not None:
        _assert_attrs(err, {"code", "message", "level"}, f"{path}/error-info")
        e: Dict[str, Any] = {
            "code": _required_attr(err, "code", path),
            "message": _required_attr(err, "message", path),
        }
        level = _attr(err, "level")
        if level is not None:
            e["level"] = _number_or_text(level)
        node["error"] = e
    cust = _first(rule, "customer-info")
    if cust is not None:
        node["customer"] = {attr.name: attr.value for attr in cust.attributes.values()}
    process = _first(rule, "customer-process")
    if process is not None:
        node["process"] = _text_content(process)
    design_id = ids.resolve(parent, rule, f"RULE-{_slug(context)}-{_slug(name)}")
    return _with_id(node, design_id)


def _parse_rule(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), "orm-rule-mapping")
    _assert_children(root, {"rule-view-info"}, "orm-rule-mapping")
    rule_views: List[Dict[str, Any]] = []
    for i, rv in enumerate(_elements(root, "rule-view-info")):
        path = f"rule-view-info[{i}]"
        _assert_attrs(rv, {"name", "code", "desc", "view-ref", "api-ref", "dataSource"}, path)
        _assert_children(rv, {"rule"}, path)
        name = _required_attr(rv, "name", path)
        node: Dict[str, Any] = {
            "name": name,
            "code": _attr(rv, "code") or name,
            "viewRef": _required_attr(rv, "view-ref", path),
        }
        for xml_name, yaml_name in (("desc", "desc"), ("api-ref", "apiRef"), ("dataSource", "dataSource")):
            value = _attr(rv, xml_name)
            if value is not None:
                node[yaml_name] = value
        node["rules"] = [
            _parse_rule_element(rule, ids, rv, name, f"{path}/rule[{ri}]")
            for ri, rule in enumerate(_elements(rv, "rule"))
        ]
        rule_views.append(_with_id(node, ids.resolve(root, rv, f"RV-{_slug(name)}")))
    return _yaml_document("rule", {"ruleViews": rule_views})



def _parse_enum(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), "enum-config")
    _assert_children(root, {"enum"}, "enum-config")
    enums: List[Dict[str, Any]] = []
    for i, enum_el in enumerate(_elements(root, "enum")):
        path = f"enum[{i}]"
        _assert_attrs(enum_el, {"name", "desc"}, path)
        _assert_children(enum_el, {"enum-value"}, path)
        name = _required_attr(enum_el, "name", path)
        node: Dict[str, Any] = {"name": name}
        desc = _attr(enum_el, "desc")
        if desc is not None:
            node["desc"] = desc
        values: List[Dict[str, Any]] = []
        for vi, value_el in enumerate(_elements(enum_el, "enum-value")):
            vpath = f"{path}/enum-value[{vi}]"
            _assert_attrs(value_el, {"value", "name", "desc"}, vpath)
            value = _required_attr(value_el, "value", vpath)
            vname = _required_attr(value_el, "name", vpath)
            item: Dict[str, Any] = {"value": _number_or_text(value), "name": vname}
            vdesc = _attr(value_el, "desc")
            if vdesc is not None:
                item["desc"] = vdesc
            generated = f"ENUM-VALUE-{_slug(name)}-{_slug(str(value))}"
            values.append(_with_id(item, ids.resolve(enum_el, value_el, generated)))
        node["values"] = values
        enums.append(_with_id(node, ids.resolve(root, enum_el, f"ENUM-{_slug(name)}")))
    return _yaml_document("enum", {"enums": enums})


def _parse_validation_info(parent: minidom.Element, path: str) -> List[Dict[str, Any]]:
    vinfo = _first(parent, "validation-info")
    if vinfo is None:
        return []
    _assert_attrs(vinfo, set(), f"{path}/validation-info")
    _assert_children(vinfo, {"validation"}, f"{path}/validation-info")
    vals: List[Dict[str, Any]] = []
    for vi, val in enumerate(_elements(vinfo, "validation")):
        vpath = f"{path}/validation[{vi}]"
        _assert_attrs(val, {"type", "value", "expression", "message"}, vpath)
        _assert_children(val, {"value"}, vpath)
        v: Dict[str, Any] = {"type": _required_attr(val, "type", vpath)}
        raw_value = _attr(val, "value")
        if raw_value is not None:
            v["value"] = _number_or_text(raw_value)
        expression = _attr(val, "expression")
        if expression is not None:
            v["expression"] = expression
        values = [_text_content(x) for x in _elements(val, "value")]
        if values:
            v["values"] = values
        msg = _attr(val, "message")
        if msg is not None:
            v["message"] = msg
        vals.append(v)
    return vals


def _parse_api(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), "api-config")
    _assert_children(root, {"api"}, "api-config")
    apis: List[Dict[str, Any]] = []
    for i, api in enumerate(_elements(root, "api")):
        path = f"api[{i}]"
        _assert_attrs(api, {"name", "desc", "system", "url", "method"}, path)
        _assert_children(api, {"request", "response"}, path)
        name = _required_attr(api, "name", path)
        system = _required_attr(api, "system", path)
        node: Dict[str, Any] = {
            "name": name,
            "system": system,
            "url": _required_attr(api, "url", path),
            "method": _required_attr(api, "method", path).upper(),
        }
        desc = _attr(api, "desc")
        if desc is not None:
            node["desc"] = desc
        request = _first(api, "request")
        if request is not None:
            _assert_attrs(request, set(), f"{path}/request")
            _assert_children(request, {"validation-info", "parameter"}, f"{path}/request")
            req: Dict[str, Any] = {}
            req_vals = _parse_validation_info(request, f"{path}/request")
            if req_vals:
                req["validations"] = req_vals
            params: List[Dict[str, Any]] = []
            for pi, param in enumerate(_elements(request, "parameter")):
                ppath = f"{path}/request/parameter[{pi}]"
                _assert_attrs(param, {"name", "in", "type", "required", "desc", "default", "rel-enum"}, ppath)
                _assert_children(param, {"validation-info"}, ppath)
                pname = _required_attr(param, "name", ppath)
                p: Dict[str, Any] = {
                    "name": pname,
                    "in": _required_attr(param, "in", ppath),
                    "type": _required_attr(param, "type", ppath),
                }
                required = _attr(param, "required")
                if required is not None:
                    p["required"] = _bool(required)
                for xml_name, yaml_name in (("desc", "desc"), ("default", "default"), ("rel-enum", "relEnum")):
                    value = _attr(param, xml_name)
                    if value is not None:
                        p[yaml_name] = value
                vals = _parse_validation_info(param, ppath)
                if vals:
                    p["validations"] = vals
                generated = f"API-PARAM-{_slug(system)}-{_slug(name)}-{_slug(pname)}"
                params.append(_with_id(p, ids.resolve(request, param, generated)))
            if params:
                req["params"] = params
            node["request"] = req
        response = _first(api, "response")
        if response is not None:
            rpath = f"{path}/response"
            _assert_attrs(response, {"type", "desc", "model-ref"}, rpath)
            _assert_children(response, {"field"}, rpath)
            r: Dict[str, Any] = {"type": _required_attr(response, "type", rpath)}
            for xml_name, yaml_name in (("desc", "desc"), ("model-ref", "modelRef")):
                value = _attr(response, xml_name)
                if value is not None:
                    r[yaml_name] = value
            fields: List[Dict[str, Any]] = []
            for fi, field in enumerate(_elements(response, "field")):
                fpath = f"{rpath}/field[{fi}]"
                _assert_attrs(field, {"name", "type", "required", "desc", "rel-enum"}, fpath)
                _assert_children(field, {"validation-info"}, fpath)
                f: Dict[str, Any] = {
                    "name": _required_attr(field, "name", fpath),
                    "type": _required_attr(field, "type", fpath),
                }
                required = _attr(field, "required")
                if required is not None:
                    f["required"] = _bool(required)
                for xml_name, yaml_name in (("desc", "desc"), ("rel-enum", "relEnum")):
                    value = _attr(field, xml_name)
                    if value is not None:
                        f[yaml_name] = value
                vals = _parse_validation_info(field, fpath)
                if vals:
                    f["validations"] = vals
                fields.append(f)
            if fields:
                r["fields"] = fields
            node["response"] = r
        generated = f"API-{_slug(system)}-{_slug(name)}"
        apis.append(_with_id(node, ids.resolve(root, api, generated)))
    return _yaml_document("api", {"apis": apis})

def _parse_systems(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, set(), "systems")
    _assert_children(root, {"system"}, "systems")
    systems: List[Dict[str, Any]] = []
    for i, system in enumerate(_elements(root, "system")):
        path = f"system[{i}]"
        _assert_attrs(system, {"name"}, path)
        _assert_children(system, {"data-info", "view-info", "rule-file-info", "information-info", "model-access-info"}, path)
        name = _required_attr(system, "name", path)
        node: Dict[str, Any] = {"name": name}
        data_info = _first(system, "data-info")
        if data_info is not None:
            node["dataRefs"] = [_required_attr(x, "name", path) for x in _elements(data_info, "data-ref")]
        view_info = _first(system, "view-info")
        if view_info is not None:
            node["viewRefs"] = [_required_attr(x, "name", path) for x in _elements(view_info, "view-ref")]
        rule_info = _first(system, "rule-file-info")
        if rule_info is not None:
            node["ruleFiles"] = [_required_attr(x, "path", path) for x in _elements(rule_info, "rule-file")]
        info_el = _first(system, "information-info")
        if info_el is not None:
            infos: List[Dict[str, Any]] = []
            for ii, inf in enumerate(_elements(info_el, "information")):
                ipath = f"{path}/information[{ii}]"
                _assert_attrs(inf, {"name", "view-ref", "rule-ref", "rule-data", "expression"}, ipath)
                _assert_children(inf, {"change-data"}, ipath)
                iname = _required_attr(inf, "name", ipath)
                n: Dict[str, Any] = {"name": iname}
                for xml_name, yaml_name in (("view-ref", "viewRef"), ("rule-ref", "ruleRef"), ("rule-data", "ruleData"), ("expression", "expression")):
                    value = _attr(inf, xml_name)
                    if value is not None:
                        n[yaml_name] = value
                change = _first(inf, "change-data")
                if change is not None:
                    n["changeData"] = _text_content(change)
                generated = f"INFO-{_slug(name)}-{_slug(iname)}"
                infos.append(_with_id(n, ids.resolve(info_el, inf, generated)))
            node["information"] = infos
        ma_info = _first(system, "model-access-info")
        if ma_info is not None:
            mas: List[Dict[str, Any]] = []
            for mi, ma in enumerate(_elements(ma_info, "model-access")):
                mpath = f"{path}/model-access[{mi}]"
                _assert_attrs(ma, {"model-ref"}, mpath)
                _assert_children(ma, {"read", "write"}, mpath)
                m: Dict[str, Any] = {"modelRef": _required_attr(ma, "model-ref", mpath)}
                for mode in ("read", "write"):
                    accesses: List[Dict[str, Any]] = []
                    for ai, access in enumerate(_elements(ma, mode)):
                        apath = f"{mpath}/{mode}[{ai}]"
                        _assert_attrs(access, {"path"}, apath)
                        _assert_children(access, {"ref"}, apath)
                        a: Dict[str, Any] = {"path": _required_attr(access, "path", apath)}
                        refs = [
                            {"view": _required_attr(ref, "view", apath), "property": _required_attr(ref, "property", apath)}
                            for ref in _elements(access, "ref")
                        ]
                        if len(refs) == 1:
                            a["ref"] = refs[0]
                        elif refs:
                            a["refs"] = refs
                        accesses.append(a)
                    if accesses:
                        m[mode] = accesses
                mas.append(m)
            node["modelAccess"] = mas
        systems.append(_with_id(node, ids.resolve(root, system, f"SYS-{_slug(name)}")))
    return _yaml_document("systems", {"systems": systems})


def _parse_action(action: minidom.Element, ids: IdFactory, parent: minidom.Element, directory_name: str, path: str) -> Dict[str, Any]:
    _assert_attrs(action, {"name", "system-ref", "rule-ref"}, path)
    _assert_children(action, {"rule", "produce-info"}, path)
    name = _required_attr(action, "name", path)
    node: Dict[str, Any] = {"name": name}
    for xml_name, yaml_name in (("system-ref", "systemRef"), ("rule-ref", "ruleRef")):
        value = _attr(action, xml_name)
        if value is not None:
            node[yaml_name] = value
    rules = _elements(action, "rule")
    if rules:
        node["rules"] = [
            _parse_rule_element(rule, ids, action, f"{directory_name}-{name}", f"{path}/rule[{ri}]")
            for ri, rule in enumerate(rules)
        ]
    pinfo = _first(action, "produce-info")
    if pinfo is not None:
        produces: List[Dict[str, Any]] = []
        for pi, prod in enumerate(_elements(pinfo, "produce")):
            ppath = f"{path}/produce[{pi}]"
            _assert_attrs(prod, {"ref", "information-ref"}, ppath)
            ref = _required_attr(prod, "ref", ppath)
            p: Dict[str, Any] = {"ref": ref}
            info_ref = _attr(prod, "information-ref")
            if info_ref is not None:
                p["informationRef"] = info_ref
            generated = f"PROD-{_slug(directory_name)}-{_slug(name)}-{_slug(ref)}"
            produces.append(_with_id(p, ids.resolve(pinfo, prod, generated)))
        node["produces"] = produces
    generated = f"ACT-{_slug(directory_name)}-{_slug(name)}"
    return _with_id(node, ids.resolve(parent, action, generated))


def _parse_action_info(parent: minidom.Element, ids: IdFactory, directory_name: str, path: str) -> List[Dict[str, Any]]:
    info = _first(parent, "action-info")
    if info is None:
        return []
    _assert_attrs(info, set(), f"{path}/action-info")
    _assert_children(info, {"action"}, f"{path}/action-info")
    return [
        _parse_action(action, ids, info, directory_name, f"{path}/action[{i}]")
        for i, action in enumerate(_elements(info, "action"))
    ]


def _parse_business(root: minidom.Element, ids: IdFactory) -> Dict[str, Any]:
    _assert_attrs(root, {"name", "desc"}, "business-config")
    _assert_children(root, {"directory-info"}, "business-config")
    name = _required_attr(root, "name", "business-config")
    business: Dict[str, Any] = {"name": name}
    desc = _attr(root, "desc")
    if desc is not None:
        business["desc"] = desc
    b_id = ids.resolve(None, root, f"BUS-{_slug(name)}", root_leading=True)
    if b_id:
        business = {"id": b_id, **business}
    dirs_el = _first(root, "directory-info")
    if dirs_el is None:
        raise ConversionError("business-config/directory-info: required")
    directories: List[Dict[str, Any]] = []
    for i, directory in enumerate(_elements(dirs_el, "directory")):
        path = f"business-config/directory[{i}]"
        _assert_attrs(directory, {"name", "type", "information-ref", "model-ref", "is-root"}, path)
        _assert_children(directory, {"subdirectory-info", "dependency-info", "action-info", "change-info"}, path)
        dname = _required_attr(directory, "name", path)
        d: Dict[str, Any] = {
            "name": dname,
            "informationRef": _required_attr(directory, "information-ref", path),
            "modelRef": _required_attr(directory, "model-ref", path),
        }
        dtype = _attr(directory, "type")
        if dtype is not None:
            d["type"] = dtype
        is_root = _attr(directory, "is-root")
        if is_root is not None:
            d["isRoot"] = _bool(is_root)
        sub_info = _first(directory, "subdirectory-info")
        if sub_info is not None:
            subs: List[Dict[str, Any]] = []
            for si, sub in enumerate(_elements(sub_info, "subdirectory")):
                spath = f"{path}/subdirectory[{si}]"
                _assert_attrs(sub, {"rel", "role", "information-ref", "mutual-exclusion", "any-one"}, spath)
                _assert_children(sub, {"back"}, spath)
                s: Dict[str, Any] = {"rel": _required_attr(sub, "rel", spath)}
                for xml_name, yaml_name in (("role", "role"), ("information-ref", "informationRef"), ("mutual-exclusion", "mutualExclusion")):
                    value = _attr(sub, xml_name)
                    if value is not None:
                        s[yaml_name] = value
                any_one = _attr(sub, "any-one")
                if any_one is not None:
                    s["anyOne"] = _bool(any_one)
                back = _first(sub, "back")
                if back is not None:
                    bpath = f"{spath}/back"
                    _assert_attrs(back, {"name"}, bpath)
                    _assert_children(back, {"action-info"}, bpath)
                    b: Dict[str, Any] = {"name": _required_attr(back, "name", bpath)}
                    actions = _parse_action_info(back, ids, f"{dname}-BACK-{b['name']}", bpath)
                    if actions:
                        b["actions"] = actions
                    s["back"] = b
                subs.append(s)
            d["subDirectories"] = subs
        dep_info = _first(directory, "dependency-info")
        if dep_info is not None:
            deps = []
            for di, dep in enumerate(_elements(dep_info, "dependency")):
                _assert_attrs(dep, {"information-ref"}, f"{path}/dependency[{di}]")
                deps.append({"informationRef": _required_attr(dep, "information-ref", path)})
            d["dependencies"] = deps
        actions = _parse_action_info(directory, ids, dname, path)
        if actions:
            d["actions"] = actions
        change = _first(directory, "change-info")
        if change is not None:
            _assert_attrs(change, {"information-ref"}, f"{path}/change-info")
            info_ref = _attr(change, "information-ref")
            if info_ref is not None:
                d["change"] = {"informationRef": info_ref}
            else:
                process = _text_content(change)
                if process:
                    d["change"] = {"process": process}
        directories.append(_with_id(d, ids.resolve(dirs_el, directory, f"DIR-{_slug(dname)}")))
    business["directories"] = directories
    return _yaml_document("business", {"business": business})


def convert_document(doc: minidom.Document, *, id_mode: str = "auto") -> Dict[str, Any]:
    root = doc.documentElement
    if root is None:
        raise ConversionError("XML document has no root element")
    if root.tagName == "directory-config":
        raise ConversionError("legacy standalone directory-config is removed; migrate to P3 business-config first")
    kind = SUPPORTED_ROOTS.get(root.tagName)
    if kind is None:
        raise ConversionError(f"unsupported DEC XML root: <{root.tagName}>")
    ids = IdFactory(id_mode)
    if kind == "config":
        return _parse_config(root, ids)
    if kind == "data":
        return _parse_data(root, ids)
    if kind == "view":
        return _parse_view(root, ids)
    if kind == "rule":
        return _parse_rule(root, ids)
    if kind == "api":
        return _parse_api(root, ids)
    if kind == "enum":
        return _parse_enum(root, ids)
    if kind == "systems":
        return _parse_systems(root, ids)
    if kind == "business":
        return _parse_business(root, ids)
    raise AssertionError(kind)


def load_xml(path: Path) -> minidom.Document:
    try:
        return minidom.parse(str(path))
    except Exception as exc:
        raise ConversionError(f"{path}: XML parse error: {exc}") from exc


def dump_yaml(data: Mapping[str, Any]) -> str:
    return yaml.dump(
        dict(data),
        Dumper=LiteralDumper,
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    )


def convert_file(src: Path, dst: Optional[Path], *, check: bool, stdout: bool, id_mode: str) -> None:
    data = convert_document(load_xml(src), id_mode=id_mode)
    text = dump_yaml(data)
    if stdout:
        sys.stdout.write(text)
    if not check and dst is not None:
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(text, encoding="utf-8")


def _xml_files(path: Path) -> List[Path]:
    return sorted([p for p in path.rglob("*.xml") if p.is_file()])


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Convert DEC XML to canonical DEC YAML")
    parser.add_argument("input", type=Path, help="XML file or directory")
    parser.add_argument("-o", "--output", type=Path, help="YAML output file, or output directory for directory input")
    parser.add_argument("--check", action="store_true", help="parse and convert in memory without writing")
    parser.add_argument("--stdout", action="store_true", help="write generated YAML to stdout (file input only)")
    parser.add_argument(
        "--id-mode",
        choices=("auto", "comments", "none"),
        default="auto",
        help="auto: restore dec-id comments or synthesize IDs; comments: only restore comments; none: omit IDs",
    )
    args = parser.parse_args(argv)

    try:
        src = args.input.resolve()
        if not src.exists():
            raise ConversionError(f"input does not exist: {src}")
        if src.is_file():
            if args.output is None and not args.check and not args.stdout:
                raise ConversionError("file input requires --output, --stdout, or --check")
            convert_file(src, args.output.resolve() if args.output else None, check=args.check, stdout=args.stdout, id_mode=args.id_mode)
            if args.check and not args.stdout:
                print(f"OK {src}")
            return 0

        if args.stdout:
            raise ConversionError("--stdout cannot be used with directory input")
        files = _xml_files(src)
        if not files:
            raise ConversionError(f"no .xml files under {src}")
        out_dir = args.output.resolve() if args.output else None
        if not args.check and out_dir is None:
            raise ConversionError("directory input requires --output directory or --check")
        errors: List[str] = []
        for file in files:
            rel = file.relative_to(src).with_suffix(".yaml")
            dst = out_dir / rel if out_dir else None
            try:
                convert_file(file, dst, check=args.check, stdout=False, id_mode=args.id_mode)
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
