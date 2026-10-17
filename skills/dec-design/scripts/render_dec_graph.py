#!/usr/bin/env python3
"""Generate a self-contained DEC HTML design site from canonical YAML.

Canonical YAML remains the source of truth. Every HTML file produced by this
script is derived output and may be overwritten on regeneration.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import shutil
import sys
import unicodedata
from collections import defaultdict, deque
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, MutableMapping, Sequence, Set, Tuple

import yaml
from dec_common import extract_info_refs, parse_change_assignments



def as_list(value: Any) -> List[Any]:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def h(value: Any) -> str:
    return html.escape("" if value is None else str(value), quote=True)


def slug(value: Any) -> str:
    s = re.sub(r"[^A-Za-z0-9_.-]+", "-", str(value or "")).strip("-").lower()
    return s or "item"


def anchor(kind: str, name: Any) -> str:
    return f"{slug(kind)}-{slug(name)}"


def discover(inputs: Sequence[str]) -> List[Path]:
    files: List[Path] = []
    for raw in inputs:
        p = Path(raw)
        if p.is_dir():
            files.extend(sorted(x for x in p.rglob("*") if x.suffix.lower() in {".yaml", ".yml"}))
        elif p.is_file():
            files.append(p)
        else:
            raise ValueError(f"input not found: {p}")
    seen: Set[Path] = set()
    out: List[Path] = []
    for p in files:
        rp = p.resolve()
        if rp not in seen:
            seen.add(rp)
            out.append(p)
    return out


def load_docs(paths: Iterable[Path]) -> List[Tuple[Path, Mapping[str, Any]]]:
    docs: List[Tuple[Path, Mapping[str, Any]]] = []
    for p in paths:
        try:
            raw = yaml.safe_load(p.read_text(encoding="utf-8"))
        except Exception as e:
            raise ValueError(f"cannot read YAML {p}: {e}") from e
        if isinstance(raw, Mapping):
            docs.append((p, raw))
    return docs


def build_catalog(docs: Sequence[Tuple[Path, Mapping[str, Any]]]) -> Dict[str, Any]:
    cat: Dict[str, Any] = {
        "datasources": {}, "connections": {}, "datas": {}, "views": {}, "ruleviews": {},
        "apis": {}, "systems": {}, "enums": {}, "infos": {}, "businesses": [], "sources": docs,
        "defaultDataSource": None, "defaultConnection": None,
    }
    for path, doc in docs:
        kind = doc.get("kind")
        if kind == "config":
            dsi = doc.get("dataSourceInfo") if isinstance(doc.get("dataSourceInfo"), Mapping) else {}
            cat["defaultDataSource"] = dsi.get("default") or cat["defaultDataSource"]
            sources = as_list(dsi.get("dataSources")) + as_list(doc.get("dataSources")) + as_list(doc.get("datasources"))
            for ds in sources:
                if isinstance(ds, Mapping) and ds.get("name"):
                    node = dict(ds); node["source"] = str(path); cat["datasources"][str(ds["name"])] = node
            ci = doc.get("connectionInfo") if isinstance(doc.get("connectionInfo"), Mapping) else {}
            cat["defaultConnection"] = ci.get("default") or cat["defaultConnection"]
            for con in as_list(ci.get("connections")) + as_list(doc.get("connections")):
                if isinstance(con, Mapping) and con.get("name"):
                    node = dict(con); node["source"] = str(path); cat["connections"][str(con["name"])] = node
        elif kind == "data":
            for item in as_list(doc.get("datas")):
                if isinstance(item, Mapping) and item.get("name"):
                    node = dict(item); node["source"] = str(path); cat["datas"][str(item["name"])] = node
        elif kind == "view":
            for item in as_list(doc.get("views")):
                if isinstance(item, Mapping) and item.get("name"):
                    node = dict(item); node["source"] = str(path); cat["views"][str(item["name"])] = node
        elif kind == "rule":
            for item in as_list(doc.get("ruleViews")):
                if isinstance(item, Mapping) and item.get("name"):
                    node = dict(item); node["source"] = str(path); cat["ruleviews"][str(item["name"])] = node
        elif kind == "api":
            for item in as_list(doc.get("apis")):
                if isinstance(item, Mapping) and item.get("name"):
                    node = dict(item); node["source"] = str(path)
                    key = f"{item.get('system')}.{item.get('name')}" if item.get("system") else str(item["name"])
                    node["key"] = key; cat["apis"][key] = node
        elif kind == "enum":
            for item in as_list(doc.get("enums")):
                if isinstance(item, Mapping) and item.get("name"):
                    node = dict(item); node["source"] = str(path); cat["enums"][str(item["name"])] = node
        elif kind == "systems":
            for system in as_list(doc.get("systems")):
                if not isinstance(system, Mapping) or not system.get("name"):
                    continue
                sys_name = str(system["name"]); snode = dict(system); snode["source"] = str(path); cat["systems"][sys_name] = snode
                for info in as_list(system.get("information")):
                    if not isinstance(info, Mapping) or not info.get("name"):
                        continue
                    key = f"{sys_name}.{info['name']}"; inode = dict(info)
                    inode.update({"key": key, "system": sys_name, "source": str(path), "dependsOn": sorted(extract_info_refs(str(info.get("expression") or "")))})
                    cat["infos"][key] = inode
        elif kind == "business":
            business = doc.get("business")
            if isinstance(business, Mapping):
                node = dict(business); node["source"] = str(path); cat["businesses"].append(node)
    return cat




def _data_property_enums(cat: Mapping[str,Any], data_name: str, property_name: str) -> Set[str]:
    found:Set[str]=set(); data=cat["datas"].get(data_name) or {}
    for table in as_list(data.get("tables")):
        if not isinstance(table,Mapping): continue
        for _,raw in (table.get("columns") or {}).items():
            if isinstance(raw,Mapping):
                ref=raw.get("ref",raw.get("refProperty"))
                if ref is not None and str(ref)==property_name and raw.get("relEnum"): found.add(str(raw.get("relEnum")))
    return found


def resolve_view_property_enums(cat: Mapping[str,Any], view_name: str, path: str) -> Set[str]:
    view=cat["views"].get(str(view_name))
    if not isinstance(view,Mapping): return set()
    parts=[x for x in str(path).split('.') if x]
    if not parts:return set()
    current_data=str(view.get("targetMain") or ""); props=view.get("properties") or {}
    for idx,part in enumerate(parts):
        raw=(props or {}).get(part) if isinstance(props,Mapping) else None
        if raw is None:return set()
        last=idx==len(parts)-1
        if isinstance(raw,Mapping) and raw.get("relation"):
            if last:return set()
            current_data=str(raw.get("data") or ""); props=raw.get("properties") or {}; continue
        if not last:return set()
        ref=raw.get("ref",raw.get("refProperty")) if isinstance(raw,Mapping) else raw
        return _data_property_enums(cat,current_data,str(ref)) if ref not in (None,"") else set()
    return set()


def information_enum_changes(cat: Mapping[str,Any], info: Mapping[str,Any]) -> List[Dict[str,Any]]:
    out=[]; view_ref=info.get("viewRef")
    if not view_ref or not info.get("changeData"):return out
    for prop,value in parse_change_assignments(str(info.get("changeData"))):
        refs=sorted(resolve_view_property_enums(cat,str(view_ref),prop))
        for enum_ref in refs:
            enum=cat["enums"].get(enum_ref) or {}; enum_value=None
            for item in as_list(enum.get("values")):
                if isinstance(item,Mapping) and str(item.get("value"))==str(value): enum_value=item; break
            out.append({"property":prop,"value":value,"enum":enum_ref,"enumValue":enum_value})
    return out


def _walk_business_actions(cat: Mapping[str,Any]) -> Iterable[Tuple[str,Mapping[str,Any]]]:
    for business in cat["businesses"]:
        for directory in as_list(business.get("directories")):
            if not isinstance(directory,Mapping):continue
            for action in as_list(directory.get("actions")):
                if isinstance(action,Mapping):yield str(directory.get("name") or ""),action
            for rel in as_list(directory.get("subDirectories")):
                if not isinstance(rel,Mapping) or not isinstance(rel.get("back"),Mapping):continue
                for action in as_list(rel["back"].get("actions")):
                    if isinstance(action,Mapping):yield str(directory.get("name") or ""),action


def ruleview_systems(cat: Mapping[str,Any], rule_name: str) -> Set[str]:
    systems:Set[str]=set(); rv=cat["ruleviews"].get(rule_name) or {}
    api_ref=rv.get("apiRef")
    if api_ref in cat["apis"] and cat["apis"][api_ref].get("system"):systems.add(str(cat["apis"][api_ref]["system"]))
    for key,info in cat["infos"].items():
        if info.get("ruleRef")==rule_name:systems.add(str(info.get("system")))
    for _,action in _walk_business_actions(cat):
        if action.get("ruleRef")==rule_name and action.get("systemRef"):systems.add(str(action.get("systemRef")))
    if not systems:
        view=cat["views"].get(str(rv.get("viewRef") or "")) or {}
        if view.get("system"):systems.add(str(view.get("system")))
    return systems


def system_view_entries(cat: Mapping[str,Any], system: str) -> List[Tuple[str,str]]:
    declared=[]; sys=cat["systems"].get(system) or {}
    for raw in as_list(sys.get("viewRefs")):
        name=raw.get("name") if isinstance(raw,Mapping) else raw
        if name:declared.append(str(name))
    owned=[name for name,v in cat["views"].items() if str(v.get("system") or "")==system]
    names=[]
    for name in owned+declared:
        if name not in names:names.append(name)
    return [(name,"owned" if str((cat["views"].get(name) or {}).get("system") or "")==system else "referenced") for name in names]


def system_enum_names(cat: Mapping[str,Any], system: str) -> List[str]:
    found:Set[str]=set(); sys=cat["systems"].get(system) or {}
    data_names={str(x.get("name") if isinstance(x,Mapping) else x) for x in as_list(sys.get("dataRefs"))}
    for dname in data_names:
        data=cat["datas"].get(dname) or {}
        for table in as_list(data.get("tables")):
            if not isinstance(table,Mapping):continue
            for raw in (table.get("columns") or {}).values():
                if isinstance(raw,Mapping) and raw.get("relEnum"):found.add(str(raw.get("relEnum")))
    for _,api in cat["apis"].items():
        if str(api.get("system") or "")!=system:continue
        req=api.get("request") if isinstance(api.get("request"),Mapping) else {}
        for par in as_list(req.get("params")):
            if isinstance(par,Mapping) and par.get("relEnum"):found.add(str(par.get("relEnum")))
        resp=api.get("response") if isinstance(api.get("response"),Mapping) else {}
        for field in as_list(resp.get("fields")):
            if isinstance(field,Mapping) and field.get("relEnum"):found.add(str(field.get("relEnum")))
    for info in cat["infos"].values():
        if str(info.get("system") or "")!=system:continue
        for item in information_enum_changes(cat,info):found.add(str(item["enum"]))
    return sorted(x for x in found if x in cat["enums"])


def system_data_names(cat: Mapping[str,Any], system: str) -> List[str]:
    owned=[name for name,d in cat["datas"].items() if str(d.get("system") or "")==system]
    declared=[]; sys=cat["systems"].get(system) or {}
    for raw in as_list(sys.get("dataRefs")):
        name=raw.get("name") if isinstance(raw,Mapping) else raw
        if name: declared.append(str(name))
    out=[]
    for name in owned+declared:
        if name in cat["datas"] and name not in out: out.append(name)
    return out


def data_property_meta(cat: Mapping[str,Any], data_name: str, prop_name: str) -> Dict[str,Any]:
    data=cat["datas"].get(data_name) or {}; raw=(data.get("properties") or {}).get(prop_name)
    meta={"type":"—","desc":"","enum":None}
    if isinstance(raw,Mapping):
        meta["type"]=raw.get("type") or "—"; meta["desc"]=raw.get("desc") or ""
    elif raw not in (None,""):
        meta["type"]=raw
    enums=_data_property_enums(cat,data_name,prop_name)
    if len(enums)==1: meta["enum"]=next(iter(enums))
    elif len(enums)>1: meta["enum"]=" / ".join(sorted(enums))
    return meta


def view_property_rows(cat: Mapping[str,Any], view: Mapping[str,Any]) -> List[Dict[str,Any]]:
    rows=[]
    def walk(props: Mapping[str,Any], current_data: str, prefix: str="", depth: int=0) -> None:
        for name,raw in props.items():
            path=f"{prefix}.{name}" if prefix else str(name)
            if isinstance(raw,Mapping) and raw.get("relation"):
                rows.append({"path":path,"depth":depth,"kind":"relation","relation":raw.get("relation"),"data":raw.get("data"),"ref":"","type":"array" if raw.get("relation")=="one-to-many" else "object","desc":raw.get("desc") or "","enum":None,"key":raw.get("key"),"relKey":raw.get("relKey")})
                walk(raw.get("properties") or {},str(raw.get("data") or ""),path,depth+1)
            else:
                ref=raw.get("ref",raw.get("refProperty")) if isinstance(raw,Mapping) else raw
                meta=data_property_meta(cat,current_data,str(ref)) if ref not in (None,"") else {"type":"—","desc":"","enum":None}
                rows.append({"path":path,"depth":depth,"kind":"property","relation":"","data":current_data,"ref":ref,"type":meta.get("type") or "—","desc":((raw.get("desc") if isinstance(raw,Mapping) else "") or meta.get("desc") or ""),"enum":meta.get("enum"),"key":"","relKey":""})
    walk(view.get("properties") or {},str(view.get("targetMain") or ""))
    return rows


def validate_visual_semantics(cat: Mapping[str,Any]) -> None:
    for business in cat["businesses"]:
        dirs=[d for d in as_list(business.get("directories")) if isinstance(d,Mapping) and d.get("name")]
        case_targets:Set[str]=set()
        for parent in dirs:
            for rel in as_list(parent.get("subDirectories")):
                if isinstance(rel,Mapping) and rel.get("role")=="case" and rel.get("rel"):case_targets.add(str(rel.get("rel")))
        for directory in dirs:
            if str(directory.get("name")) in case_targets and as_list(directory.get("dependencies")):
                raise ValueError(f"Directory {directory.get('name')}: case target must not also declare dependencies; case is already the branch gate")



def _is_generated_html_file(path: Path) -> bool:
    if not path.is_file() or path.suffix.lower() != ".html":
        return False
    try:
        return "dec-design/scripts/render_dec_graph.py" in path.read_text(encoding="utf-8", errors="ignore")[:4096]
    except OSError:
        return False


def cleanup_legacy_system_directories(site_root: Path, systems: Iterable[str]) -> None:
    """Remove only legacy root-level System directories produced by older versions.

    Before the `system/<system>/` namespace existed, generated System pages lived
    directly under `<site-root>/<system>/`. We delete a legacy directory only when
    every contained file is an HTML artifact generated by this script. Unknown or
    user-owned content is never removed.
    """
    for system in systems:
        legacy = site_root / slug(system)
        if not legacy.is_dir() or legacy.name in {"system", "directory"}:
            continue
        entries = list(legacy.iterdir())
        if entries and all(_is_generated_html_file(x) for x in entries):
            shutil.rmtree(legacy)

def cleanup_legacy_root_pages(site_root: Path, pages: Mapping[str, str], prefixes: Iterable[str | None]) -> None:
    """Remove recognized old prefixed indexes; preserve unknown/user-owned files."""
    current = set(pages.values())
    for prefix in prefixes:
        if not prefix:
            continue
        for suffix in ("", "-index", "-datasources", "-data", "-views", "-ruleviews", "-apis", "-systems", "-enums"):
            name = f"{slug(prefix)}{suffix}.html"
            path = site_root / name
            if name not in current and not path.is_symlink() and _is_generated_html_file(path):
                path.unlink()


def page_names(out: Path, systems: Iterable[str]) -> Dict[str, str]:
    pages = {
        "business": "directories.html",
        "index": "index.html",
        "datasources": "datasources.html",
        "data": "data.html",
        "views": "views.html",
        "ruleviews": "ruleviews.html",
        "apis": "apis.html",
        "systems": "systems.html",
        "enums": "enums.html",
    }
    for system in systems:
        pages[f"system:{system}"] = f"system/{slug(system)}/index.html"
        pages[f"info:{system}"] = f"system/{slug(system)}/information.html"
    return pages


def subdir_pages(pages: Mapping[str,str], system: str | None = None) -> Dict[str,str]:
    # Business Directory details live one level below the site root (directory/),
    # while System pages live two levels below it (system/<system>/).
    if system:
        out={k:(v if v.startswith("../../") else "../../"+v) for k,v in pages.items()}
        out.update({"index":"../../"+pages["index"],"systems":"../../"+pages["systems"],"business":"../../"+pages["business"],"datasources":"../../"+pages["datasources"],
                    "root:data":"../../"+pages["data"],"root:views":"../../"+pages["views"],"root:ruleviews":"../../"+pages["ruleviews"],"root:apis":"../../"+pages["apis"],"root:enums":"../../"+pages["enums"],
                    "data":"data.html","views":"views.html","ruleviews":"ruleviews.html","apis":"apis.html","enums":"enums.html",
                    f"system:{system}":"index.html",f"info:{system}":"information.html"})
        for other in [k.split(":",1)[1] for k in pages if k.startswith("system:") and k!=f"system:{system}"]:
            out[f"system:{other}"]=f"../{slug(other)}/index.html"; out[f"info:{other}"]=f"../{slug(other)}/information.html"
        return out
    return {k:(v if v.startswith("../") else "../"+v) for k,v in pages.items()}


def apply_system_entity_routes(pages: MutableMapping[str,str], cat: Mapping[str,Any], system: str, scoped: Mapping[str,Any]) -> None:
    """Add entity-specific links for System-scoped pages.

    System pages may render referenced entities owned by another System. Prefer a
    local anchor when that entity is rendered in the current System slice;
    otherwise route to an owning/associated System page, falling back to the
    root global index for that design kind.
    """
    local_kinds = {
        "data": ("datas", "data.html"),
        "view": ("views", "views.html"),
        "ruleview": ("ruleviews", "ruleviews.html"),
        "api": ("apis", "apis.html"),
        "enum": ("enums", "enums.html"),
    }
    for kind,(collection,page) in local_kinds.items():
        for name in scoped.get(collection,{}):
            pages[f"target:{kind}:{name}"] = f"{page}#{anchor(kind,name)}"

    for name,node in cat.get("datas",{}).items():
        key=f"target:data:{name}"
        if key in pages: continue
        owner=str(node.get("system") or "")
        pages[key]=(f"../{slug(owner)}/data.html#{anchor('data',name)}" if owner in cat.get("systems",{}) else f"{pages.get('root:data', pages.get('data','#'))}#{anchor('data',name)}")
    for name,node in cat.get("views",{}).items():
        key=f"target:view:{name}"
        if key in pages: continue
        owner=str(node.get("system") or "")
        pages[key]=(f"../{slug(owner)}/views.html#{anchor('view',name)}" if owner in cat.get("systems",{}) else f"{pages.get('root:views', pages.get('views','#'))}#{anchor('view',name)}")
    for name,node in cat.get("apis",{}).items():
        key=f"target:api:{name}"
        if key in pages: continue
        owner=str(node.get("system") or "")
        pages[key]=(f"../{slug(owner)}/apis.html#{anchor('api',name)}" if owner in cat.get("systems",{}) else f"{pages.get('root:apis', pages.get('apis','#'))}#{anchor('api',name)}")
    for name in cat.get("ruleviews",{}):
        key=f"target:ruleview:{name}"
        if key in pages: continue
        owners=sorted(ruleview_systems(cat,name))
        owner=owners[0] if owners else ""
        pages[key]=(f"../{slug(owner)}/ruleviews.html#{anchor('ruleview',name)}" if owner in cat.get("systems",{}) else f"{pages.get('root:ruleviews', pages.get('ruleviews','#'))}#{anchor('ruleview',name)}")
    for name in cat.get("enums",{}):
        key=f"target:enum:{name}"
        if key in pages: continue
        owners=sorted(sys for sys in cat.get("systems",{}) if name in system_enum_names(cat,sys))
        owner=owners[0] if owners else ""
        pages[key]=(f"../{slug(owner)}/enums.html#{anchor('enum',name)}" if owner else f"{pages.get('root:enums', pages.get('enums','#'))}#{anchor('enum',name)}")


def system_slice(cat: Mapping[str,Any], system: str) -> Dict[str,Any]:
    view_names={n for n,_ in system_view_entries(cat,system)}
    rv_names={n for n in cat["ruleviews"] if system in ruleview_systems(cat,n)}
    api_names={k for k,a in cat["apis"].items() if str(a.get("system") or "")==system}
    enum_names=set(system_enum_names(cat,system))
    info_nodes=system_information_context(cat["infos"],system)
    data_names=set(system_data_names(cat,system))
    for vn in view_names:
        v=cat["views"].get(vn) or {}; data_names.add(str(v.get("targetMain") or ""))
        for _,raw in flatten_view_properties(v.get("properties") or {}):
            if isinstance(raw,Mapping) and raw.get("data"): data_names.add(str(raw.get("data")))
    out=dict(cat)
    out["datas"]={k:v for k,v in cat["datas"].items() if k in data_names}
    out["views"]={k:v for k,v in cat["views"].items() if k in view_names}
    out["ruleviews"]={k:v for k,v in cat["ruleviews"].items() if k in rv_names}
    out["apis"]={k:v for k,v in cat["apis"].items() if k in api_names}
    out["enums"]={k:v for k,v in cat["enums"].items() if k in enum_names}
    out["infos"]=info_nodes
    return out


def href(pages: Mapping[str, str], kind: str, name: Any) -> str:
    s = str(name or "")
    if not s:
        return "#"
    specific = pages.get(f"target:{kind}:{s}")
    if specific:
        return specific
    if kind == "info":
        system = s.split(".", 1)[0] if "." in s else ""
        return f"{pages.get('info:'+system, pages.get('systems','#'))}#{anchor('info', s)}"
    if kind == "directory":
        return pages.get("directory:"+s, f"{pages['business']}#{anchor('directory', s)}")
    if kind == "system":
        return pages.get("system:"+s, f"{pages['systems']}#{anchor('system', s)}")
    key_map = {
        "datasource": "datasources", "data": "data", "view": "views", "ruleview": "ruleviews",
        "api": "apis", "system": "systems", "enum": "enums",
    }
    page = pages.get(key_map.get(kind, kind), "#")
    return f"{page}#{anchor(kind, s)}"


def link(pages: Mapping[str, str], kind: str, name: Any, label: Any | None = None, cls: str = "xref") -> str:
    if name in (None, ""):
        return "<span class='muted'>—</span>"
    return f"<a class='{h(cls)}' href='{h(href(pages, kind, name))}'>{h(label if label is not None else name)}</a>"


def nav_html(pages: Mapping[str, str], current: str = "") -> str:
    # System is the primary navigation entry for View / RuleView / API / Enum / Information.
    # Their global pages still exist as stable cross-link targets, but are intentionally not
    # top-level navigation items so readers enter those design facts through their System.
    items = [
        ("index", "Overview"), ("systems", "System"), ("business", "Directory"),
        ("datasources", "DataSource"), ("data", "Data"),
    ]
    links = []
    for key, label in items:
        active = " active" if current == key else ""
        links.append(f"<a class='nav-link{active}' href='{h(pages[key])}'>{h(label)}</a>")
    return "<nav class='site-nav'>" + "".join(links) + "</nav>"


CSS = r'''
:root{--bg:#f6f8f5;--card:#fff;--ink:#233039;--muted:#62706a;--line:#dbe3d8;--brand:#315f4b;--brand2:#eaf4ed;--accent:#8a5c14;--link:#155e75}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.55 ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.page{max-width:1500px;margin:auto;padding:20px}.hero{background:#fdfefc;border:1px solid var(--line);border-radius:18px;padding:24px 28px;margin-bottom:14px}.hero h1{margin:0 0 4px;font-size:28px}.hero p{margin:0;color:var(--muted)}.site-nav{position:sticky;top:0;z-index:20;display:flex;flex-wrap:wrap;gap:6px;background:rgba(246,248,245,.95);backdrop-filter:blur(6px);padding:9px 2px 12px}.nav-link{padding:7px 11px;border-radius:9px;color:#39514a;text-decoration:none;border:1px solid transparent}.nav-link:hover,.nav-link.active{background:#fff;border-color:var(--line)}.panel,.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:18px;margin:14px 0;box-shadow:0 1px 2px rgba(15,23,42,.03)}.panel h2,.card h2,.card h3{margin-top:0}.card-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:14px}.meta{display:flex;flex-wrap:wrap;gap:7px;margin:8px 0}.badge{display:inline-flex;align-items:center;background:#eef3ef;border:1px solid #d8e2da;color:#3f554c;border-radius:999px;padding:2px 8px;font-size:12px}.badge.em{background:#fff7df;border-color:#ead8a4;color:#765310}.muted{color:var(--muted)}.xref{color:var(--link);text-decoration:none;border-bottom:1px dotted #7aa6b2}.xref:hover{border-bottom-style:solid}.kv{display:grid;grid-template-columns:minmax(110px,160px) 1fr;gap:5px 12px;margin:8px 0}.kv dt{color:#64736b}.kv dd{margin:0;min-width:0}.code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;background:#f7f8f6;border:1px solid #e4e8e2;border-radius:9px;padding:9px;overflow:auto}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:8px 9px;border-bottom:1px solid #e6ebe4;vertical-align:top}th{background:#f7f9f6;color:#526159;font-weight:650}.small{font-size:13px}.generated-banner{padding:9px 12px;background:#fff8e7;border:1px solid #efdca4;border-radius:10px;color:#715414;margin:10px 0}.graph-layout{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:14px}.graph-canvas{position:relative;min-width:0}.graph-wrap{height:680px;border:1px solid #dfe6dc;border-radius:14px;background:#fbfcfa;overflow:hidden}.interactive-svg{width:100%;height:100%;touch-action:none;cursor:grab}.interactive-svg:active{cursor:grabbing}.graph-toolbar{position:absolute;right:12px;top:12px;z-index:5;display:flex;gap:6px}.graph-toolbar button{width:34px;height:34px;border:1px solid #ccd7cd;border-radius:9px;background:#fff;cursor:pointer;font-size:18px}.inspector{border:1px solid #dfe6dc;border-radius:14px;background:#fbfcfa;padding:15px;max-height:680px;overflow:auto}.inspector h3{margin:0 0 10px}.inspector-section{padding:10px 0;border-top:1px solid #e4e9e2}.inspector-section:first-child{border-top:0}.inspector-section h4{margin:0 0 6px;font-size:14px}.inspector ul{margin:5px 0;padding-left:20px}.legend{display:flex;gap:14px;flex-wrap:wrap;margin:9px 0 12px;font-size:13px;color:#58675f}.swatch{display:inline-block;width:16px;height:11px;border:1px solid #819089;border-radius:3px;margin-right:5px}.swatch.dir{background:#dcefdc}.swatch.act{background:#fff1bf;border-radius:50%}.swatch.dep{background:#dfeaf8;border-color:#7b9bbd}.swatch.change{background:#fae4cc}.swatch.prod{background:#e7f3da;border:3px double #79945b}.node-title{font-size:14px;font-weight:700;fill:#253238}.node-detail{font-size:11px;fill:#586760}.edge-label{font-size:10.5px;font-weight:600;paint-order:stroke;stroke:#fbfcfa;stroke-width:4px;stroke-linejoin:round}.dir-node{fill:#dcefdc;stroke:#789477;stroke-width:1.6}.action-node{fill:#fff1bf;stroke:#b89d47;stroke-width:1.5}.dependency-node{fill:#dfeaf8;stroke:#6f91b4;stroke-width:1.5}.produce-node{fill:#eef7e7;stroke:#76915e;stroke-width:3}.change-node{fill:#fae4cc;stroke:#b17b43;stroke-width:1.5}.info-node{fill:#e8f4e9;stroke:#769477;stroke-width:1.5}.external-info-node{fill:#f5f5f3;stroke:#8a918c;stroke-width:1.5;stroke-dasharray:6 4}.info-node-back{fill:#f7fbf7;stroke:#9bb39b;stroke-width:1}.viz-node{cursor:move}.viz-node:focus>*:first-child{stroke:#2563eb!important;stroke-width:3!important}.notice{color:#617069;font-size:13px}.source-note{margin:18px 2px;color:#77837d;font-size:12px}.quick-links{display:flex;flex-wrap:wrap;gap:8px}.quick-links a{padding:5px 9px;background:#edf5f1;border-radius:8px;text-decoration:none;color:#315f4b}.section-note{color:#66756d}.empty{padding:24px;color:#758078;text-align:center}.ref-list{display:flex;flex-wrap:wrap;gap:6px}.ref-list a{display:inline-block;padding:3px 7px;background:#eff5f2;border-radius:7px;text-decoration:none;color:#315f4b}@media(max-width:1000px){.graph-layout{grid-template-columns:1fr}.inspector{max-height:none}.graph-wrap{height:600px}}
.inline-information{height:100%;overflow:auto;overscroll-behavior:contain;cursor:text;user-select:text;padding:8px;background:#fff;border:1px solid #cbdacb;border-radius:8px;font:12px/1.5 ui-sans-serif,sans-serif;color:#233039;overflow-wrap:anywhere}.inline-information h4{margin:8px 0 4px}.inline-information p{margin:4px 0}.inspector pre,.inline-information pre{margin:4px 0 10px;white-space:pre-wrap;overflow-wrap:anywhere;font:11px/1.5 ui-monospace,monospace}.info-expand-control{cursor:pointer}.info-expand-control circle{fill:#fff;stroke:#617c65;stroke-width:1.5}.info-expand-control text{font-size:16px;font-weight:bold;fill:#406047;pointer-events:none}.info-expand-control:focus circle{stroke:#2563eb;stroke-width:3}.rule-face{fill:#efe5fa;stroke:#9368bb;stroke-width:2}.rule-detail-node .inline-information{background:#faf6ff;border-color:#d8c4eb}

'''

JS = r'''
(function(){
 function point(svg,x,y){var p=svg.createSVGPoint();p.x=x;p.y=y;return p.matrixTransform(svg.getScreenCTM().inverse());}
 function node(svg,id){return Array.from(svg.querySelectorAll('.viz-node')).find(function(n){return n.dataset.nodeId===id;});}
 function nodeBox(svg,id){var n=node(svg,id);if(!n)return null;var face=n.querySelector('.info-face, .rule-face'),b=(face||n).getBBox(),dx=parseFloat(n.dataset.dx||0),dy=parseFloat(n.dataset.dy||0);return{x:b.x+dx,y:b.y+dy,w:b.width,h:b.height,cx:b.x+dx+b.width/2,cy:b.y+dy+b.height/2};}
 function endpoints(a,b){var dx=b.cx-a.cx,dy=b.cy-a.cy;if(Math.abs(dx)>=Math.abs(dy)){var s=dx>=0?1:-1;return[a.cx+s*a.w/2,a.cy,b.cx-s*b.w/2,b.cy];}var s2=dy>=0?1:-1;return[a.cx,a.cy+s2*a.h/2,b.cx,b.cy-s2*b.h/2];}
 function updateEdge(svg,g){if(g.style.display==='none')return;var a=nodeBox(svg,g.dataset.source),b=nodeBox(svg,g.dataset.target);if(!a||!b)return;var e=endpoints(a,b),x1=e[0],y1=e[1],x2=e[2],y2=e[3],bend=parseFloat(g.dataset.bend||0),path=g.querySelector('.edge-path'),label=g.querySelector('.edge-label'),mx=(x1+x2)/2,my=(y1+y2)/2,d,lx,ly;if(bend){my+=bend;d='M '+x1+' '+y1+' Q '+mx+' '+my+', '+x2+' '+y2;lx=mx;ly=my-5;}else{d='M '+x1+' '+y1+' C '+mx+' '+y1+', '+mx+' '+y2+', '+x2+' '+y2;lx=mx;ly=(y1+y2)/2-6;}path.setAttribute('d',d);if(label){label.setAttribute('x',lx);label.setAttribute('y',ly);}}
 function updateEdges(svg,nodeId){svg.querySelectorAll('.graph-edge').forEach(function(g){if(!nodeId||g.dataset.source===nodeId||g.dataset.target===nodeId)updateEdge(svg,g);});}
 function zoom(svg,factor,cx,cy){var vb=svg.viewBox.baseVal,pt=point(svg,cx,cy),nw=vb.width*factor,nh=vb.height*factor,rx=(pt.x-vb.x)/vb.width,ry=(pt.y-vb.y)/vb.height;vb.x=pt.x-rx*nw;vb.y=pt.y-ry*nh;vb.width=nw;vb.height=nh;}
 function reset(svg){var v=(svg.dataset.originalViewbox||'0 0 1000 700').split(/\s+/).map(Number),vb=svg.viewBox.baseVal;vb.x=v[0];vb.y=v[1];vb.width=v[2];vb.height=v[3];svg.querySelectorAll('.viz-node').forEach(function(n){n.dataset.dx='0';n.dataset.dy='0';n.removeAttribute('transform');delete n.dataset.expansionDirection;if(n.dataset.expansionNode)n.style.display='none';});svg.querySelectorAll('.rule-detail-node, .rule-detail-edge').forEach(function(n){n.style.display='none';});svg.querySelectorAll('.info-expand-control').forEach(function(c){c.setAttribute('aria-expanded','false');c.querySelector('text').textContent='＋';});svg.querySelectorAll('.graph-edge').forEach(function(e){if(e.dataset.expansionEdge)e.style.display='none';});updateEdges(svg);}
 function positionNode(n){n.setAttribute('transform','translate('+parseFloat(n.dataset.dx||0)+' '+parseFloat(n.dataset.dy||0)+')');}
 function overlaps(a,b){return a.x<b.x+b.w+24&&a.x+a.w+24>b.x&&a.y<b.y+b.h+24&&a.y+a.h+24>b.y;}
 function placeRulePanel(svg,source,panel,direction){var a=nodeBox(svg,source.dataset.nodeId),size=nodeBox(svg,panel.dataset.nodeId),b={x:a.cx-size.w/2,y:a.cy-size.h/2,w:size.w,h:size.h};if(direction==='up')b.y=a.y-b.h-60;if(direction==='down')b.y=a.y+a.h+60;if(direction==='left')b.x=a.x-b.w-60;if(direction==='right')b.x=a.x+a.w+60;var obstacles=Array.from(svg.querySelectorAll('.viz-node')).filter(function(n){return n!==panel&&n!==source&&n.style.display!=='none';}).map(function(n){return nodeBox(svg,n.dataset.nodeId);});for(var i=0;i<=obstacles.length;i++){var hit=obstacles.filter(function(o){return overlaps(b,o);});if(!hit.length)break;if(direction==='up')b.y=Math.min.apply(null,hit.map(function(o){return o.y;}))-b.h-40;else if(direction==='down')b.y=Math.max.apply(null,hit.map(function(o){return o.y+o.h;}))+40;else if(direction==='left')b.x=Math.min.apply(null,hit.map(function(o){return o.x;}))-b.w-40;else b.x=Math.max.apply(null,hit.map(function(o){return o.x+o.w;}))+40;}panel.dataset.dx=b.x;panel.dataset.dy=b.y;positionNode(panel);}
 function revealRulePair(svg,source,panel){var a=nodeBox(svg,source.dataset.nodeId),b=nodeBox(svg,panel.dataset.nodeId),x=Math.min(a.x,b.x)-30,y=Math.min(a.y,b.y)-30,w=Math.max(a.x+a.w,b.x+b.w)-x+30,h=Math.max(a.y+a.h,b.y+b.h)-y+30,v=svg.viewBox.baseVal,f=Math.max(1,w/v.width,h/v.height);v.width*=f;v.height*=f;if(x<v.x)v.x=x;if(y<v.y)v.y=y;if(x+w>v.x+v.width)v.x=x+w-v.width;if(y+h>v.y+v.height)v.y=y+h-v.height;}
 function syncExpansion(svg){
   var nodes=Array.from(svg.querySelectorAll('.viz-node')),visible=new Set(),queue=nodes.filter(function(n){return n.dataset.expansionRoot;});
   while(queue.length){var n=queue.shift(),id=n.dataset.nodeId;if(visible.has(id))continue;visible.add(id);if(n.dataset.expansionDirection)JSON.parse(n.dataset.expansionTargets||'[]').forEach(function(id){var target=node(svg,id);if(target)queue.push(target);});}
   nodes.forEach(function(n){if(n.dataset.expansionNode){n.style.display=visible.has(n.dataset.nodeId)?'':'none';if(!visible.has(n.dataset.nodeId))delete n.dataset.expansionDirection;}});
   svg.querySelectorAll('.info-expand-control').forEach(function(c){if(c.dataset.expansion){var active=c.closest('.viz-node').dataset.expansionDirection===c.dataset.direction;c.setAttribute('aria-expanded',String(active));c.querySelector('text').textContent=active?'−':'＋';}});
   svg.querySelectorAll('.graph-edge').forEach(function(e){if(e.dataset.expansionEdge){var source=node(svg,e.dataset.source);e.style.display=visible.has(e.dataset.source)&&source.dataset.expansionDirection&&visible.has(e.dataset.target)?'':'none';}});
 }
 function toggleInformation(source,direction){
   var svg=source.closest('.interactive-svg');if(source.dataset.expansionDirection===direction)delete source.dataset.expansionDirection;else source.dataset.expansionDirection=direction;
   syncExpansion(svg);
   if(source.dataset.expansionDirection)JSON.parse(source.dataset.expansionTargets).forEach(function(id){var target=node(svg,id);placeRulePanel(svg,source,target,direction);revealRulePair(svg,source,target);});updateEdges(svg);
 }
 function selectNode(g){activate(g);if(g.dataset.expansionTargets)toggleInformation(g,g.dataset.expansionDirection||'right');}
 function toggleRulePanel(control){if(control.dataset.expansion){toggleInformation(control.closest('.viz-node'),control.dataset.direction);return;}var source=control.closest('.viz-node'),svg=source.closest('.interactive-svg'),panel=node(svg,control.dataset.panelId),expanded=control.getAttribute('aria-expanded')!=='true';if(!panel)return;panel.style.display=expanded?'':'none';control.setAttribute('aria-expanded',String(expanded));control.querySelector('text').textContent=expanded?'−':'＋';svg.querySelectorAll('.rule-detail-edge').forEach(function(edge){if(edge.dataset.target===panel.dataset.nodeId)edge.style.display=expanded?'':'none';});if(expanded){placeRulePanel(svg,source,panel,control.dataset.direction);revealRulePair(svg,source,panel);}updateEdges(svg);}
 function activate(g){var p=g.closest('.panel'),box=p&&p.querySelector('.inspector-body');if(!box)return;var id=g.dataset.nodeId,tpl=Array.from(p.querySelectorAll('template.node-template')).find(function(t){return t.dataset.detailId===id;});if(tpl)box.innerHTML=tpl.innerHTML;}
 document.querySelectorAll('.interactive-svg').forEach(function(svg){
   updateEdges(svg);var pan=null,drag=null,moved=false;
   svg.addEventListener('wheel',function(e){if(e.target.closest('.inline-information'))return;e.preventDefault();zoom(svg,e.deltaY>0?1.12:.89,e.clientX,e.clientY);},{passive:false});
   svg.addEventListener('pointerdown',function(e){if(e.target.closest('.inline-information')||e.target.closest('.info-expand-control'))return;var n=e.target.closest('.viz-node');moved=false;if(n){var p=point(svg,e.clientX,e.clientY);drag={node:n,id:n.dataset.nodeId,start:p,dx:parseFloat(n.dataset.dx||0),dy:parseFloat(n.dataset.dy||0)};n.setPointerCapture(e.pointerId);e.preventDefault();e.stopPropagation();}else{pan={x:e.clientX,y:e.clientY,vx:svg.viewBox.baseVal.x,vy:svg.viewBox.baseVal.y};svg.setPointerCapture(e.pointerId);}});
   svg.addEventListener('pointermove',function(e){if(drag){var p=point(svg,e.clientX,e.clientY),dx=drag.dx+p.x-drag.start.x,dy=drag.dy+p.y-drag.start.y;if(Math.abs(dx-drag.dx)+Math.abs(dy-drag.dy)>2)moved=true;drag.node.dataset.dx=dx;drag.node.dataset.dy=dy;positionNode(drag.node);updateEdges(svg,drag.id);}else if(pan){var r=svg.getBoundingClientRect(),vb=svg.viewBox.baseVal;vb.x=pan.vx-(e.clientX-pan.x)*vb.width/r.width;vb.y=pan.vy-(e.clientY-pan.y)*vb.height/r.height;}});
   svg.addEventListener('pointerup',function(){drag=null;pan=null;});svg.addEventListener('pointercancel',function(){drag=null;pan=null;});
   svg.querySelectorAll('.viz-node').forEach(function(g){g.addEventListener('click',function(e){var control=e.target.closest('.info-expand-control');if(control){toggleRulePanel(control);return;}if(!moved&&!e.target.closest('.inline-information'))selectNode(g);});g.addEventListener('keydown',function(e){if(e.target.closest('.inline-information'))return;if(e.key==='Enter'||e.key===' '){e.preventDefault();var control=e.target.closest('.info-expand-control');if(control)toggleRulePanel(control);else selectNode(g);}});});
 });
 document.querySelectorAll('[data-graph-action]').forEach(function(btn){btn.addEventListener('click',function(){var panel=btn.closest('.panel'),svg=panel&&panel.querySelector('.interactive-svg');if(!svg)return;var a=btn.dataset.graphAction,r=svg.getBoundingClientRect();if(a==='in')zoom(svg,.82,r.left+r.width/2,r.top+r.height/2);else if(a==='out')zoom(svg,1.22,r.left+r.width/2,r.top+r.height/2);else if(a==='reset')reset(svg);});});
})();
'''


def html_start(title: str, subtitle: str, pages: Mapping[str, str], current: str) -> str:
    return (f"<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            f"<meta name='generator' content='dec-design/scripts/render_dec_graph.py'><title>{h(title)}</title><style>{CSS}</style></head><body><div class='page'>"
            f"<div class='hero'><h1>{h(title)}</h1><p>{h(subtitle)}</p></div>{nav_html(pages,current)}"
            "<div class='generated-banner'><strong>Generated artifact.</strong> 该 HTML 必须由 <code>scripts/render_dec_graph.py</code> 从 canonical YAML 生成；YAML 修改后请重新生成并覆盖旧 HTML，禁止手工维护 HTML 设计事实。</div>")


def html_end(docs: Sequence[Tuple[Path, Mapping[str, Any]]]) -> str:
    return f"<div class='source-note'>Generated from {len(docs)} canonical DEC YAML document(s). No external runtime/CDN is required.</div><script>{JS}</script></div></body></html>"


def toolbar() -> str:
    return "<div class='graph-toolbar'><button type='button' data-graph-action='in' title='放大'>＋</button><button type='button' data-graph-action='out' title='缩小'>－</button><button type='button' data-graph-action='reset' title='重置视图和节点位置'>⟳</button></div>"


def _defs() -> str:
    colors = {"flow":"#4b5563","case":"#0f766e","dependency":"#6b7280","action":"#64748b","produce":"#4d7c0f","change":"#92400e","back":"#b45309","depends":"#475569","rule":"#8854b8"}
    out = ["<defs>"]
    for k,c in colors.items():
        out.append(f'<marker id="arrow-{k}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="{c}"/></marker>')
    out.append('<filter id="soft-shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#0f172a" flood-opacity="0.10"/></filter></defs>')
    return "".join(out)


def wrap(text: str, limit: int = 28) -> List[str]:
    s = str(text or "")
    if len(s) <= limit:
        return [s]
    out: List[str] = []
    while len(s) > limit:
        cut = max(s.rfind(" ",0,limit), s.rfind(".",0,limit), s.rfind("/",0,limit))
        if cut < limit//3: cut = limit
        else: cut += 1
        out.append(s[:cut].strip()); s=s[cut:].strip()
    if s: out.append(s)
    return out


def _svg_text_lines(x: float, y: float, text: str, *, cls: str = "node-title", max_chars: int = 22, max_lines: int = 3, dy: int = 17) -> str:
    lines = wrap(text, max_chars)[:max_lines]
    start = y - ((len(lines)-1)*dy)/2
    return "".join(f'<text x="{x:.1f}" y="{start+i*dy:.1f}" text-anchor="middle" class="{cls}">{h(line)}</text>' for i,line in enumerate(lines))


def _edge_path(x1: float, y1: float, x2: float, y2: float, *, kind: str, label: str, source_id: str, target_id: str, bend: float = 0) -> str:
    styles = {"flow":("#4b5563","",2),"case":("#0f766e","",2),"dependency":("#6b7280","4 4",1.7),"action":("#64748b","",1.6),"produce":("#4d7c0f","",1.7),"change":("#92400e","",1.7),"back":("#b45309","8 5",2),"depends":("#475569","",1.8),"rule":("#8854b8","5 3",1.8)}
    color,dash,width=styles.get(kind,("#64748b","",1.6)); dash_attr=f' stroke-dasharray="{dash}"' if dash else ""
    if bend:
        mx=(x1+x2)/2; my=(y1+y2)/2+bend; d=f'M {x1:.1f} {y1:.1f} Q {mx:.1f} {my:.1f}, {x2:.1f} {y2:.1f}'; lx,ly=mx,my-5
    else:
        mx=(x1+x2)/2; d=f'M {x1:.1f} {y1:.1f} C {mx:.1f} {y1:.1f}, {mx:.1f} {y2:.1f}, {x2:.1f} {y2:.1f}'; lx,ly=mx,(y1+y2)/2-6
    return (f'<g class="graph-edge" data-source="{h(source_id)}" data-target="{h(target_id)}" data-kind="{h(kind)}" data-bend="{bend}">'
            f'<path class="edge-path" d="{d}" fill="none" stroke="{color}" stroke-width="{width}"{dash_attr} marker-end="url(#arrow-{kind})"/>'
            + (f'<text x="{lx:.1f}" y="{ly:.1f}" text-anchor="middle" class="edge-label" fill="{color}">{h(label)}</text>' if label else "") + "</g>")


def _interactive_group(node_id: str, inner: str, html_anchor: str = "") -> str:
    aid = f' id="{h(html_anchor)}"' if html_anchor else ""
    return f'<g{aid} class="viz-node" data-node-id="{h(node_id)}" data-dx="0" data-dy="0" tabindex="0">{inner}</g>'


def recognizer(info: Mapping[str, Any]) -> str:
    if info.get("expression"): return "expression"
    if info.get("ruleData"): return "ruleData"
    if info.get("ruleRef"): return "ruleRef"
    return "unknown"


def info_detail_html(info: Mapping[str, Any], pages: Mapping[str, str], cat: Mapping[str,Any] | None = None) -> str:
    deps = as_list(info.get("dependsOn"))
    basic = [
        ("Design ID", info.get("id")), ("Information Key", info.get("key")), ("System", link(pages,"system",info.get("system"))),
        ("Description", info.get("description") or info.get("desc") or "—"), ("Recognizer", f"<span class='badge'>{h(recognizer(info))}</span>"),
    ]
    if info.get("viewRef"): basic.append(("View", link(pages,"view",info.get("viewRef"))))
    if info.get("ruleRef"): basic.append(("RuleView", link(pages,"ruleview",info.get("ruleRef"))))
    rows = "".join(f"<dt>{h(k)}</dt><dd>{v if isinstance(v,str) and v.startswith('<') else h(v)}</dd>" for k,v in basic if v not in (None,""))
    parts=[f"<div class='inspector-section'><h4>Information</h4><dl class='kv'>{rows}</dl></div>"]
    if deps:
        parts.append("<div class='inspector-section'><h4>Dependencies</h4><div class='ref-list'>"+"".join(link(pages,"info",d) for d in deps)+"</div></div>")
    parts.append(information_rules_html(info, cat or {"ruleviews": {}}))
    if cat is not None:
        changes=information_enum_changes(cat,info)
        if changes:
            parts.append("<div class='inspector-section'><h4>Enum change mapping</h4><ul>")
            for item in changes:
                ev=item.get("enumValue") or {}; label=f"{item.get('value')}"+(f" · {ev.get('name')}" if ev.get('name') else "")
                parts.append(f"<li><code>{h(item.get('property'))}</code> → {link(pages,'enum',item.get('enum'))} = <strong>{h(label)}</strong></li>")
            parts.append("</ul></div>")
    return "".join(parts)


def action_data_sources(action: Mapping[str, Any], business: Mapping[str, Any], cat: Mapping[str, Any]) -> List[Tuple[str, Any, str]]:
    ruleview = cat.get("ruleviews", {}).get(str(action.get("ruleRef")), {})
    rules = [r for r in as_list(ruleview.get("rules")) if isinstance(r, Mapping)]
    result = []
    for rule in rules or [{}]:
        choices = [(action.get("dataSource"), "Action"), (rule.get("dataSource"), "Rule"),
                   (ruleview.get("dataSource"), "RuleView"), (business.get("dataSource"), "Business")]
        source, origin = next(((source, origin) for source, origin in choices if source), (None, "未配置"))
        result.append((str(rule.get("name") or action.get("name") or ""), source, origin))
    return result


def action_detail_html(action: Mapping[str, Any], pages: Mapping[str, str], business=None, cat=None) -> str:
    parts=["<div class='inspector-section'><h4>Action</h4><dl class='kv'>",
           f"<dt>Name</dt><dd>{h(action.get('name'))}</dd>",f"<dt>Design ID</dt><dd>{h(action.get('id') or '—')}</dd>"]
    if action.get("systemRef"): parts += ["<dt>System</dt><dd>",link(pages,"system",action.get("systemRef")),"</dd>"]
    if action.get("ruleRef"): parts += ["<dt>RuleView</dt><dd>",link(pages,"ruleview",action.get("ruleRef")),"</dd>"]
    else: parts += ["<dt>Type</dt><dd><span class='badge em'>Custom Action</span></dd>"]
    parts.append(f"<dt>Action DataSource</dt><dd>{link(pages,'datasource',action['dataSource']) if action.get('dataSource') else '未覆盖'}</dd>")
    if business is not None:
        parts.append(f"<dt>Business 默认 DataSource</dt><dd>{link(pages,'datasource',business['dataSource']) if business.get('dataSource') else '未配置'}</dd>")
        for rule, source, origin in action_data_sources(action, business, cat or {}):
            parts.append(f"<dt>{h(rule)} · 有效 DataSource</dt><dd>{link(pages,'datasource',source) if source else '未配置'} · {h(origin)}</dd>")
    parts.append("</dl></div>")
    prods=[p for p in as_list(action.get("produces")) if isinstance(p,Mapping)]
    if prods:
        parts.append("<div class='inspector-section'><h4>Produce</h4><ul>")
        for p in prods:
            info = f" → {link(pages,'info',p.get('informationRef'))}" if p.get("informationRef") else ""
            parts.append(f"<li><strong>{h(p.get('ref'))}</strong>{info}</li>")
        parts.append("</ul></div>")
    return "".join(parts)


def directory_detail_html(d: Mapping[str, Any], pages: Mapping[str, str], business=None, cat=None) -> str:
    rows=[("Design ID",d.get("id")),("Name",d.get("name")),("Type",d.get("type") or "normal"),("Root","yes" if d.get("isRoot") else "no"),
          ("Information",link(pages,"info",d.get("informationRef"))),("Model / View",link(pages,"view",d.get("modelRef")))]
    parts=["<div class='inspector-section'><h4>Directory</h4><dl class='kv'>"+"".join(f"<dt>{h(k)}</dt><dd>{v if isinstance(v,str) and v.startswith('<') else h(v)}</dd>" for k,v in rows if v not in (None,""))+"</dl></div>"]
    deps=[x for x in as_list(d.get("dependencies")) if isinstance(x,Mapping) and x.get("informationRef")]
    if deps: parts.append("<div class='inspector-section'><h4>Dependencies</h4><div class='ref-list'>"+"".join(link(pages,"info",x["informationRef"]) for x in deps)+"</div></div>")
    acts=[x for x in as_list(d.get("actions")) if isinstance(x,Mapping)]
    if acts:
        parts.append("<div class='inspector-section'><h4>Actions</h4>")
        for a in acts: parts.append(action_detail_html(a,pages,business,cat))
        parts.append("</div>")
    ch=d.get("change")
    if isinstance(ch,Mapping):
        parts.append("<div class='inspector-section'><h4>Change</h4>")
        if ch.get("informationRef"): parts.append(link(pages,"info",ch.get("informationRef")))
        if ch.get("process"): parts.append(f"<div class='code'>{h(ch.get('process'))}</div>")
        parts.append("</div>")
    rels=[r for r in as_list(d.get("subDirectories")) if isinstance(r,Mapping)]
    if rels:
        parts.append("<div class='inspector-section'><h4>Relations / Back</h4><ul>")
        for r in rels:
            bits=[link(pages,"directory",r.get("rel"))]
            if r.get("role"): bits.append(f"role={h(r.get('role'))}")
            if r.get("informationRef"): bits.append("when="+link(pages,"info",r.get("informationRef")))
            b=r.get("back")
            if isinstance(b,Mapping):
                ba=[]
                for a in as_list(b.get("actions")):
                    if isinstance(a,Mapping): ba.append(h(a.get("name")))
                bits.append(f"<strong>BACK {h(b.get('name'))}</strong>"+(f" [{', '.join(ba)}]" if ba else ""))
            parts.append("<li>"+" · ".join(bits)+"</li>")
        parts.append("</ul></div>")
    return "".join(parts)


def system_information_context(infos: Mapping[str, Dict[str, Any]], system: str) -> Dict[str, Dict[str, Any]]:
    local={k for k,v in infos.items() if str(v.get("system"))==system}; needed=set(local); q=deque(sorted(local))
    while q:
        key=q.popleft()
        for dep in infos.get(key,{}).get("dependsOn",[]):
            if dep in infos and dep not in needed: needed.add(dep); q.append(dep)
    out={}
    for key in sorted(needed):
        node=dict(infos[key]); node["external"]=str(node.get("system"))!=system; out[key]=node
    return out


def information_relation_label(info: Mapping[str, Any], dependency: str | None = None) -> str:
    """Label only flat, unambiguous expressions; keep mixed/grouped logic verbatim in the page."""
    expression = str(info.get("expression") or "").strip()
    ref = r"[A-Za-z_][A-Za-z0-9_-]*\.[A-Za-z_][A-Za-z0-9_-]*"
    term = rf"(?:not\s+)?{ref}"
    for operator, label in (("or", "OR · 任一"), ("and", "AND · 全部")):
        if re.fullmatch(rf"{term}(?:\s+{operator}\s+{term})+", expression):
            terms = re.split(rf"\s+{operator}\s+", expression)
            negative = {t.split()[1] for t in terms if re.match(r"not\s+", t)}
            positive = {t for t in terms if not re.match(r"not\s+", t)}
            if negative & positive:
                return "表达式引用"
            if dependency in negative:
                return operator.upper() + " · NOT"
            return label
    if re.fullmatch(rf"not\s+{ref}", expression):
        return "NOT · 取反"
    return "表达式引用"


def information_text_lines(text: str, limit: int, max_lines: int) -> List[str]:
    """Bound SVG text by display width (CJK is two units), not Python character count."""
    lines = []; current = ""; width = 0
    for char in str(text):
        units = 0 if unicodedata.combining(char) else (2 if unicodedata.east_asian_width(char) in "WF" else 1)
        if char == "\n" or width + units > limit:
            lines.append(current); current = ""; width = 0
            if char == "\n":
                continue
        current += char; width += units
    if current or not lines:
        lines.append(current)
    if len(lines) > max_lines:
        lines = lines[:max_lines]
        lines[-1] = lines[-1][:-1] + "…"
    return lines


def information_layout(infos: Mapping[str, Dict[str, Any]]) -> Dict[str, Tuple[float, float]]:
    # Layout each connected component independently, then put isolated facts in a grid.
    # Arrow direction remains dependent -> dependency; states are never linked by inference.
    deps = {k: sorted({d for d in v.get("dependsOn", []) if d in infos}) for k, v in infos.items()}
    neighbors = {k: set(deps[k]) for k in infos}
    for key, targets in deps.items():
        for target in targets:
            neighbors[target].add(key)
    remaining = {k for k in infos if neighbors[k]}; components = []
    while remaining:
        pending = [min(remaining)]; component = set()
        while pending:
            key = pending.pop()
            if key in component:
                continue
            component.add(key); pending.extend(sorted(neighbors[key] - component))
        remaining -= component; components.append(component)
    positions = {}; top = 60.0
    for component in components:
        incoming = {k: 0 for k in component}; levels = {k: 0 for k in component}
        for key in component:
            for target in deps[key]:
                incoming[target] += 1
        queue = deque(sorted(k for k in component if incoming[k] == 0)); visited = 0
        while queue:
            key = queue.popleft(); visited += 1
            for target in deps[key]:
                levels[target] = max(levels[target], levels[key] + 1)
                incoming[target] -= 1
                if incoming[target] == 0:
                    queue.append(target)
        if visited != len(component):
            raise ValueError("Information dependency cycle: " + ", ".join(sorted(component)))
        by_level = defaultdict(list)
        for key in sorted(component):
            by_level[levels[key]].append(key)
        for level in sorted(by_level, reverse=True):
            def desired_y(key):
                return sum(positions[d][1] for d in deps[key]) / len(deps[key]) if deps[key] else top
            last_y = top - 188
            for key in sorted(by_level[level], key=lambda k: (desired_y(k), bool(infos[k].get("external")), k)):
                y = max(desired_y(key), last_y + 188)
                positions[key] = (60 + level * 440, y); last_y = y
        top = max(positions[k][1] for k in component) + 136 + 90
    isolated = sorted(k for k in infos if not neighbors[k])
    for index, key in enumerate(isolated):
        positions[key] = (60 + (index % 2) * 440, top + (index // 2) * 188)
    return positions


def information_rules_html(info: Mapping[str, Any], cat: Mapping[str, Any]) -> str:
    """Use the same canonical rule sections in graph panels and the inspector."""
    parts = ["<div class='inspector-section information-rules'><h4>识别规则</h4>"]
    source = recognizer(info)
    parts.append(f"<p>规则来源: <strong>{h(source)}</strong></p>")
    for key in ("expression", "ruleData"):
        if info.get(key):
            parts.append(f"<pre>{h(info[key])}</pre>")
    if info.get("ruleRef"):
        name = str(info["ruleRef"]); ruleview = cat["ruleviews"].get(name)
        parts.append(f"<h4>RuleView · {h(name)}</h4>")
        if ruleview is None:
            parts.append("<p>当前设计输入未包含该 RuleView 定义。</p>")
        else:
            for key in ("id", "code", "desc", "viewRef", "apiRef", "dataSource"):
                if ruleview.get(key):
                    parts.append(f"<p>{h(key)}: {h(ruleview[key])}</p>")
            for index, rule in enumerate(as_list(ruleview.get("rules")), 1):
                if not isinstance(rule, Mapping):
                    continue
                parts.append(f"<h4>Rule {index} · {h(rule.get('name'))}</h4>")
                datasource = rule.get("dataSource") or ruleview.get("dataSource") or "runtime/project default"
                parts.append(f"<p>有效 DataSource: {h(datasource)}</p>")
                for key, value in rule.items():
                    if value is None:
                        continue
                    text = json.dumps(value, ensure_ascii=False, indent=2) if isinstance(value, (Mapping, list)) else str(value)
                    parts.append(f"<p><strong>{h(key)}</strong></p><pre>{h(text)}</pre>")
    if source == "unknown":
        parts.append("<p>当前 Information 未提供规则定义。</p>")
    parts.append("</div>")
    if info.get("changeData"):
        parts.append(f"<div class='inspector-section'><h4>Change data</h4><p>物化 changeData</p><pre>{h(info['changeData'])}</pre></div>")
    return "".join(parts)


def information_inline_html(info: Mapping[str, Any], cat: Mapping[str, Any]) -> str:
    """Embed canonical rule content so opening a node never requires a second page."""
    parts = [f"<p>Design ID: {h(info.get('id') or '—')}</p>",
             f"<p>{h(info.get('description') or info.get('desc') or '')}</p>"]
    if info.get("viewRef"):
        parts.append(f"<p>View: <strong>{h(info['viewRef'])}</strong></p>")
    parts.append(information_rules_html(info, cat))
    return "".join(parts)


def article_information_graph(infos: Mapping[str, Dict[str, Any]], system: str, pages: Mapping[str,str], cat: Mapping[str,Any]) -> Tuple[str,str]:
    pos = information_layout(infos)
    width = max(840, max((x for x, _ in pos.values()), default=500) + 340)
    height = max(400, max((y for _, y in pos.values()), default=200) + 186)
    parts=[f'<svg class="interactive-svg" viewBox="0 0 {width} {height}" data-original-viewbox="0 0 {width} {height}" role="img" aria-label="Information dependency graph for {h(system)}">',_defs()]
    templates=[]
    involved = {k for k, info in infos.items() if any(d in infos for d in info.get("dependsOn", []))}
    involved.update(d for info in infos.values() for d in info.get("dependsOn", []) if d in infos)
    isolated = set(infos) - involved
    if isolated:
        heading_y = min(pos[k][1] for k in isolated) - 24
        parts.append(f'<text x="60" y="{heading_y}" class="node-detail" data-isolated-heading="true">独立 Information（本图中无组合引用边）</text>')
    for key, info in infos.items():
        for dep in info.get("dependsOn", []):
            if dep in pos:
                sx, sy = pos[key]; tx, ty = pos[dep]
                parts.append(_edge_path(sx+280,sy+68,tx,ty+68,kind="depends",label=information_relation_label(info,dep),source_id=f"info:{key}",target_id=f"info:{dep}"))
    for key, info in infos.items():
        x, y = pos[key]; external = bool(info.get("external")); composite = bool(info.get("expression"))
        desc = str(info.get("description") or info.get("desc") or recognizer(info))
        inner = []
        if composite:
            inner.append(f'<rect x="{x+6}" y="{y-6}" width="280" height="136" rx="10" class="info-node-back"/>')
        inner.append(f'<rect x="{x}" y="{y}" width="280" height="136" rx="10" class="info-face {"external-info-node" if external else "info-node"}" filter="url(#soft-shadow)"/>')
        inner.append(f'<title>{h(key)} · {h(desc)}</title>')
        for text, limit, count, center, dy, cls in ((key,32,2,y+29,18,"node-title"),(desc,42,3,y+77,15,"node-detail")):
            lines = information_text_lines(text, limit, count)
            start = center - (len(lines)-1)*dy/2
            for i, line in enumerate(lines):
                inner.append(f'<text x="{x+140}" y="{start+i*dy}" text-anchor="middle" class="{cls}">{h(line)}</text>')
        badge = information_relation_label(info) if composite else "原子 · " + recognizer(info)
        if external:
            badge += " · external"
        inner.append(f'<text x="{x+140}" y="{y+119}" text-anchor="middle" class="node-detail">{h(badge)}</text>')
        panels = []
        for direction, bx, by, label in (("up",x+140,y-14,"上"),("down",x+140,y+150,"下"),
                                         ("left",x-14,y+68,"左"),("right",x+294,y+68,"右")):
            panel_id = f"rule:{key}:{direction}"
            inner.append(f'<g class="info-expand-control" data-direction="{direction}" data-panel-id="{h(panel_id)}" role="button" tabindex="0" aria-expanded="false" aria-label="向{label}展开或收起 {h(key)} 的规则">'
                         f'<circle cx="{bx}" cy="{by}" r="11"/><text x="{bx}" y="{by+4}" text-anchor="middle">＋</text></g>')
            panel_inner = '<rect x="0" y="0" width="320" height="340" rx="12" class="rule-face"/>'
            panel_inner += _svg_text_lines(160,23,key,cls="node-title",max_chars=32,max_lines=1)
            panel_inner += '<text x="160" y="44" text-anchor="middle" class="node-detail">规则详情 · 再点原节点对应的 − 收起</text>'
            panel_inner += f'<foreignObject x="10" y="54" width="300" height="276"><div xmlns="http://www.w3.org/1999/xhtml" class="inline-information" tabindex="0">{information_inline_html(info,cat)}</div></foreignObject>'
            panel = _interactive_group(panel_id, panel_inner)
            panel = panel.replace('class="viz-node"', 'class="viz-node rule-detail-node" style="display:none"', 1)
            edge = _edge_path(0,0,0,0,kind="rule",label="规则详情",source_id=f"info:{key}",target_id=panel_id)
            edge = edge.replace('class="graph-edge"', 'class="graph-edge rule-detail-edge" style="display:none"', 1)
            panels.extend([edge, panel])
            templates.append(f'<template class="node-template" data-detail-id="{h(panel_id)}">'
                             f'{info_detail_html(info,pages,cat)}</template>')
        group = _interactive_group(f"info:{key}","".join(inner),anchor("info",key))
        group = group.replace('tabindex="0"', f'tabindex="0" data-info-x="{x}" data-info-y="{y}" data-isolated="{str(key in isolated).lower()}"', 1)
        parts.append(group)
        parts.extend(panels)
        templates.append(f'<template class="node-template" data-detail-id="info:{h(key)}">{info_detail_html(info,pages,cat)}</template>')
    parts.append("</svg>")
    return "".join(parts),"".join(templates)


def _business_flow_layout(business: Mapping[str, Any]) -> Tuple[Dict[str, Tuple[int,int]], List[Tuple[str,str,Mapping[str,Any],str]]]:
    dirs=[d for d in as_list(business.get("directories")) if isinstance(d,Mapping) and d.get("name")]; names={str(d["name"]) for d in dirs}
    flow=[]; adj:Dict[str,List[str]]=defaultdict(list); indeg={n:0 for n in names}
    for d in dirs:
        owner=str(d["name"])
        for rel in as_list(d.get("subDirectories")):
            if not isinstance(rel,Mapping) or str(rel.get("rel") or "") not in names: continue
            other=str(rel["rel"]); role=str(rel.get("role") or "")
            src,dst,kind=(owner,other,"case") if role=="case" else (other,owner,"flow")
            flow.append((src,dst,rel,kind)); adj[src].append(dst); indeg[dst]+=1
    q=deque(sorted(n for n,v in indeg.items() if v==0)); level={n:0 for n in q}
    while q:
        n=q.popleft()
        for t in adj.get(n,[]):
            level[t]=max(level.get(t,0),level[n]+1); indeg[t]-=1
            if indeg[t]==0:q.append(t)
    order={str(d["name"]):i for i,d in enumerate(dirs)}
    for i,d in enumerate(dirs): level.setdefault(str(d["name"]),i)
    by:Dict[int,List[str]]=defaultdict(list)
    for n,l in level.items():by[l].append(n)
    for l in by:by[l].sort(key=lambda n:order.get(n,999))
    maxrows=max((len(v) for v in by.values()),default=1); pos={}
    for l,items in sorted(by.items()):
        for row,n in enumerate(items):pos[n]=(240+l*430,220+(maxrows-len(items))*150+row*330)
    return pos,flow


def business_information_expansion(root_id: str, ref: str, inner: str,
                                   box: Tuple[float, float, float, float],
                                   cat: Mapping[str, Any], pages: Mapping[str, str]) -> Tuple[str, str]:
    """One reachable graph per occurrence; shared dependencies are not duplicated."""
    if ref not in cat["infos"]:
        return _interactive_group(root_id, inner), ""
    reachable = {}
    pending = [ref]
    while pending:
        key = pending.pop()
        if key in reachable:
            continue
        info = cat["infos"].get(key)
        if info is None:
            raise ValueError(f"Missing Information definition: {key}")
        reachable[key] = info
        pending.extend(info.get("dependsOn", []))
    information_layout(reachable)  # Reject cycles before emitting interactive state.
    ids = {key: root_id if key == ref else f"expand:{root_id}:info:{key}" for key in reachable}
    parts, templates = [], []
    for key, info in reachable.items():
        nid = ids[key]
        dependencies = info.get("dependsOn", [])
        targets = [ids[dep] for dep in dependencies] or [f"expand:{root_id}:rule:{key}"]
        if key == ref:
            body, (x, y, width, height) = inner, box
        else:
            x, y, width, height = 0, 0, 280, 136
            body = '<rect x="0" y="0" width="280" height="136" rx="10" class="rule-face"/>'
            body += _svg_text_lines(140, 27, key, max_chars=32, max_lines=2)
            body += _svg_text_lines(140, 67, str(info.get("description") or ""), cls="node-detail", max_chars=34, max_lines=3)
            body += f'<text x="140" y="120" text-anchor="middle" class="node-detail">{h(information_relation_label(info) if dependencies else recognizer(info))}</text>'
        for direction, cx, cy, label in (("up", x+width/2, y-14, "上"), ("down", x+width/2, y+height+14, "下"),
                                         ("left", x-14, y+height/2, "左"), ("right", x+width+14, y+height/2, "右")):
            body += (f'<g class="info-expand-control" data-expansion="true" data-direction="{direction}" role="button" tabindex="0" aria-expanded="false" aria-label="向{label}展开或收起 {h(key)}">'
                     f'<circle cx="{cx}" cy="{cy}" r="11"/><text x="{cx}" y="{cy+4}" text-anchor="middle">＋</text></g>')
        group = _interactive_group(nid, body)
        attrs = f'data-expansion-targets="{h(json.dumps(targets))}"'
        if key == ref:
            attrs += ' data-expansion-root="true"'
        else:
            attrs += ' data-expansion-node="true" style="display:none"'
        parts.append(group.replace('class="viz-node"', f'class="viz-node" {attrs}', 1))
        detail = info_detail_html(info, pages, cat)
        templates.append(f'<template class="node-template" data-detail-id="{h(nid)}">{detail}</template>')
        for index, target in enumerate(targets):
            kind = "depends" if dependencies else "rule"
            label = information_relation_label(info, dependencies[index]) if dependencies else "规则详情"
            edge = _edge_path(0, 0, 0, 0, kind=kind, label=label, source_id=nid, target_id=target)
            parts.append(edge.replace('class="graph-edge"', 'class="graph-edge" data-expansion-edge="true" style="display:none"', 1))
        if not dependencies:
            rule_id = targets[0]
            body = '<rect x="0" y="0" width="320" height="340" rx="12" class="rule-face"/>'
            body += _svg_text_lines(160, 25, key, max_chars=32, max_lines=1)
            body += f'<foreignObject x="10" y="45" width="300" height="285"><div xmlns="http://www.w3.org/1999/xhtml" class="inline-information" tabindex="0">{information_inline_html(info,cat)}</div></foreignObject>'
            parts.append(_interactive_group(rule_id, body).replace('class="viz-node"', 'class="viz-node" data-expansion-node="true" style="display:none"', 1))
            templates.append(f'<template class="node-template" data-detail-id="{h(rule_id)}">{detail}</template>')
    return "".join(parts), "".join(templates)


def article_business_graph(business: Mapping[str,Any], cat: Mapping[str,Any], pages: Mapping[str,str]) -> Tuple[str,str]:
    dirs=[d for d in as_list(business.get("directories")) if isinstance(d,Mapping) and d.get("name")]; dmap={str(d["name"]):d for d in dirs}; pos,flow=_business_flow_layout(business)
    if not pos:return "<div class='empty'>No Directory</div>",""
    width=max(1250,max(x for x,_ in pos.values())+570); height=max(760,max(y for _,y in pos.values())+520)
    parts=[f'<svg class="interactive-svg" viewBox="0 0 {width} {height}" data-original-viewbox="0 0 {width} {height}" role="img" aria-label="Business Directory map">',_defs()]; templates=[]
    for src,dst,rel,kind in flow:
        sx,sy=pos[src];tx,ty=pos[dst];labels=[]
        if kind=="case":labels.append("case")
        if rel.get("informationRef"):labels.append(str(rel.get("informationRef")))
        parts.append(_edge_path(sx+180,sy+35,tx,ty+35,kind=kind,label=" · ".join(labels) or "next",source_id=f"dir:{src}",target_id=f"dir:{dst}"))
    for owner,d in dmap.items():
        for rel in as_list(d.get("subDirectories")):
            if not isinstance(rel,Mapping) or not isinstance(rel.get("back"),Mapping):continue
            other=str(rel.get("rel") or "")
            if other not in pos:continue
            ox,oy=pos[owner];tx,ty=pos[other];b=rel["back"]
            acts=",".join(str(a.get("name")) for a in as_list(b.get("actions")) if isinstance(a,Mapping) and a.get("name")); label=f"BACK {b.get('name','')}"+(f" · {acts}" if acts else "")
            parts.append(_edge_path(ox,oy+58,tx+180,ty+58,kind="back",label=label,source_id=f"dir:{owner}",target_id=f"dir:{other}",bend=55))
    for name,d in dmap.items():
        x,y=pos[name]
        # dependency nodes to the upper-left; semantic direction is Directory → prerequisite Information
        for i,dep in enumerate([v for v in as_list(d.get("dependencies")) if isinstance(v,Mapping) and v.get("informationRef")]):
            ref=str(dep["informationRef"]); dx,dy=x-190,y-85-i*68; nid=f"dep:{name}:{i}"
            parts.append(_edge_path(x,y+18,dx+155,dy+24,kind="dependency",label="depend",source_id=f"dir:{name}",target_id=nid))
            inner=f'<rect x="{dx}" y="{dy}" width="155" height="48" rx="10" class="info-face dependency-node"/><text x="{dx+77.5}" y="{dy+29}" text-anchor="middle" class="node-detail">{h(ref)}</text>'
            expanded, detail = business_information_expansion(nid, ref, inner, (dx,dy,155,48), cat, pages)
            parts.append(expanded)
            templates.append(detail or f'<template class="node-template" data-detail-id="{h(nid)}"><div class="inspector-section"><h4>Dependency</h4>{link(pages,"info",ref)}</div></template>')
        # actions and produces below directory
        for i,a in enumerate([v for v in as_list(d.get("actions")) if isinstance(v,Mapping) and v.get("name")]):
            aid=f"action:{name}:{i}"; ax,ay=x+25+i*115,y+120
            parts.append(_edge_path(x+90,y+70,ax+36,ay,kind="action",label="",source_id=f"dir:{name}",target_id=aid))
            inner=f'<circle cx="{ax+36}" cy="{ay+36}" r="36" class="action-node"/><text x="{ax+36}" y="{ay+40}" text-anchor="middle" class="node-detail">{h(a.get("name"))}</text>'
            routes = action_data_sources(a, business, cat)
            route_label = "DS: " + ", ".join(dict.fromkeys(str(source or "未配置") for _, source, _ in routes))
            inner += f'<text x="{ax+36}" y="{ay+88}" text-anchor="middle" class="node-detail">{h(route_label)}</text>'
            parts.append(_interactive_group(aid,inner)); templates.append(f'<template class="node-template" data-detail-id="{h(aid)}">{action_detail_html(a,pages,business,cat)}</template>')
            for j,p in enumerate([p for p in as_list(a.get("produces")) if isinstance(p,Mapping) and p.get("ref")]):
                pid=f"produce:{name}:{i}:{j}"; px,py=ax-6+j*145,ay+105
                parts.append(_edge_path(ax+36,ay+72,px+68,py,kind="produce",label="PRODUCE",source_id=aid,target_id=pid))
                inner=f'<rect x="{px}" y="{py}" width="136" height="48" rx="8" class="produce-node"/><text x="{px+68}" y="{py+29}" text-anchor="middle" class="node-detail">{h(p.get("ref"))}</text>'
                parts.append(_interactive_group(pid,inner)); info=p.get("informationRef")
                templates.append(f'<template class="node-template" data-detail-id="{h(pid)}"><div class="inspector-section"><h4>Produce</h4><dl class="kv"><dt>Ref</dt><dd>{h(p.get("ref"))}</dd><dt>Information</dt><dd>{link(pages,"info",info) if info else "—"}</dd></dl></div></template>')
        ch=d.get("change")
        if isinstance(ch,Mapping):
            cid=f"change:{name}"; cx,cy=x+225,y-90; pts=f"{cx},{cy+27} {cx+20},{cy} {cx+120},{cy} {cx+140},{cy+27} {cx+120},{cy+54} {cx+20},{cy+54}"
            parts.append(_edge_path(x+180,y+18,cx,cy+27,kind="change",label="CHANGE",source_id=f"dir:{name}",target_id=cid))
            label=str(ch.get("informationRef") or "process")
            inner=f'<polygon points="{pts}" class="change-node"/>'+_svg_text_lines(cx+70,cy+30,label,cls="node-detail",max_chars=20,max_lines=3,dy=14)
            parts.append(_interactive_group(cid,inner))
            change_detail = (
                link(pages, "info", ch.get("informationRef"))
                if ch.get("informationRef")
                else '<div class="code">' + h(ch.get("process")) + "</div>"
            )
            info = cat["infos"].get(str(ch.get("informationRef") or ""))
            if info:
                change_detail += info_detail_html(info, pages, cat)
            if ch.get("informationRef") and ch.get("process"):
                change_detail += '<div class="code">' + h(ch["process"]) + '</div>'
            templates.append(
                f'<template class="node-template" data-detail-id="{h(cid)}">'
                f'<div class="inspector-section"><h4>Change</h4>{change_detail}</div>'
                "</template>"
            )
        # directory last so it visually sits above edges
        desc=str(d.get("informationRef") or "Directory")
        inner=f'<rect x="{x}" y="{y}" width="180" height="70" rx="9" class="dir-node" filter="url(#soft-shadow)"/>'+_svg_text_lines(x+90,y+27,name,max_chars=18,max_lines=1)+f'<text x="{x+90}" y="{y+53}" text-anchor="middle" class="node-detail">{h(desc)}</text>'
        parts.append(_interactive_group(f"dir:{name}",inner,anchor("directory",name))); templates.append(f'<template class="node-template" data-detail-id="dir:{h(name)}">{directory_detail_html(d,pages,business,cat)}</template>')
    parts.append("</svg>")
    return "".join(parts),"".join(templates)


def render_business_map_detail(b: Mapping[str,Any], cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]]) -> str:
    svg,templates=article_business_graph(b,cat,pages)
    parts=[html_start(f"Business Directory Map · {b.get('name')}",str(b.get('desc') or ''),pages,"business")]
    default_source = link(pages, "datasource", b["dataSource"]) if b.get("dataSource") else "未配置"
    parts.append(f"<div class='panel'><h2>流程默认 DataSource：{default_source}</h2><p>优先级：Action → Rule → RuleView → Business。逻辑名称在执行时绑定已注册的 DEC 适配器。</p></div>")
    parts.append(f"<div class='panel'><div class='legend'><span><i class='swatch dir'></i>Directory</span><span><i class='swatch act'></i>Action</span><span><i class='swatch dep'></i>Dependency</span><span><i class='swatch prod'></i>Produce</span><span><i class='swatch change'></i>Change</span><span>BACK = 反向虚线</span></div><div class='notice'>滚轮或 ＋/－ 缩放；拖动空白区域平移；拖动节点重新布局，连线自动跟随。点击节点在右侧查看结构化详情；点击依赖 Information 或四向 ＋ 可逐级展开组合条件与规则，再次点击收起。</div><div class='graph-layout'><div class='graph-canvas'>{toolbar()}<div class='graph-wrap'>{svg}</div></div><aside class='inspector'><h3>Directory detail</h3><div class='inspector-body'><div class='muted'>点击图中节点查看详细信息。</div></div></aside>{templates}</div></div>")
    parts.append("<div class='panel'><h2>Action 数据源（含 Back）</h2><div class='card-grid'>")
    for directory in as_list(b.get("directories")):
        if not isinstance(directory, Mapping): continue
        for action in as_list(directory.get("actions")):
            if isinstance(action, Mapping):
                parts.append(f"<article class='card'><h3>{h(directory.get('name'))}</h3>{action_detail_html(action,pages,b,cat)}</article>")
        for sub in as_list(directory.get("subDirectories")):
            if not isinstance(sub, Mapping) or not isinstance(sub.get("back"), Mapping): continue
            for action in as_list(sub["back"].get("actions")):
                if isinstance(action, Mapping):
                    parts.append(f"<article class='card'><h3>Back {h(sub['back'].get('name'))}</h3>{action_detail_html(action,pages,b,cat)}</article>")
    parts.append("</div></div>")
    return "".join(parts)+html_end(docs)


def render_business_html(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]]) -> str:
    parts=[html_start("DEC Directory","Directory 下列出所有 Business Directory Map；点击业务进入可交互详细图。",pages,"business")]
    parts.append("<div class='panel'><h2>Business Directory Maps</h2><div class='card-grid'>")
    if not cat["businesses"]: parts.append("<div class='empty'>No kind: business YAML found.</div>")
    for b in cat["businesses"]:
        target=f"directory/{slug(b.get('name'))}.html"
        dirs=len([d for d in as_list(b.get('directories')) if isinstance(d,Mapping)])
        parts.append(f"<a class='card' style='text-decoration:none;color:inherit' href='{h(target)}'><h2>{h(b.get('name'))}</h2><p>{h(b.get('desc') or '')}</p><div class='meta'><span class='badge'>Directories {dirs}</span><span class='badge'>DataSource {h(b.get('dataSource') or '未配置')}</span></div><p class='xref'>Open Business Directory Map →</p></a>")
    parts.append("</div></div>")
    return "".join(parts)+html_end(docs)

def render_information_html(system: str, cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]]) -> str:
    context=system_information_context(cat["infos"],system); svg,templates=article_information_graph(context,system,pages,cat)
    parts=[html_start(f"DEC Information Graph · {system}","依赖箭头语义：依赖者 → 被依赖者。例如 user.effective → user.activated / user.certified。",pages,"systems")]
    parts.append(f"<div class='panel'><h2>Information dependency graph · {h(system)}</h2><p class='section-note'>绿色为本 System Information；灰色虚线为跨 System 依赖上下文。</p><div class='notice'>点击原信息框在右侧查看详情与跳转链接；点击节点上、下、左、右的 ＋，在该方向打开紫色规则框，并用紫色虚线连接原节点；点击对应的 − 收起。支持多个方向同时打开、拖动节点和详情框；规则内容可独立滚动、复制。紫色线仅表示详情归属，不是业务依赖。</div><div class='graph-layout information-layout'><div class='graph-canvas'>{toolbar()}<div class='graph-wrap'>{svg}</div></div><aside class='inspector'><h3>Information detail</h3><div class='inspector-body'><div class='muted'>点击原信息框查看 Information、System、View、RuleView 和依赖详情；四周 ＋ 可独立打开图内规则框。</div></div></aside>{templates}</div></div>")
    expressions = [(key, info) for key, info in sorted(context.items()) if info.get("expression")]
    if expressions:
        parts.append("<div class='panel'><h2>组合条件（canonical expression）</h2><p class='section-note'>OR 表示任一满足；AND 表示全部满足；NOT 表示取反。混合或带括号的条件以完整表达式为准，图中的引用边不代表执行顺序。</p>")
        for key, info in expressions:
            parts.append(f"<h3>{link(pages,'info',key)}</h3><div class='code'>{h(info['expression'])}</div>")
        parts.append("</div>")
    # readable list, complete instead of raw key=value dump
    parts.append("<div class='panel'><h2>Information definitions</h2><div class='card-grid'>")
    for key in sorted(context):
        i=context[key]; parts.append(f"<article class='card' id='{h(anchor('info-card',key))}'><h3>{h(key)}</h3>{info_detail_html(i,pages,cat)}</article>")
    parts.append("</div></div>")
    return "".join(parts)+html_end(docs)


def backlink_lists(cat: Mapping[str,Any]) -> Dict[str,Dict[str,List[Tuple[str,str,str]]]]:
    out={"datasource":defaultdict(list),"data":defaultdict(list),"view":defaultdict(list),"ruleview":defaultdict(list),"api":defaultdict(list),"system":defaultdict(list),"enum":defaultdict(list)}
    def add(target_kind: str, target: Any, source_kind: str, source: Any, label_text: str) -> None:
        if target not in (None, "") and source not in (None, ""):
            out[target_kind][str(target)].append((source_kind, str(source), label_text))
    for dname,d in cat["datas"].items():
        if d.get("system"): add("system",d["system"],"data",dname,f"Data {dname} owner")
        for t in as_list(d.get("tables")):
            if isinstance(t,Mapping) and t.get("dataSource"): add("datasource",t["dataSource"],"data",dname,f"Data {dname}")
            if isinstance(t,Mapping):
                for col,val in (t.get("columns") or {}).items():
                    if isinstance(val,Mapping) and val.get("relEnum"): add("enum",val["relEnum"],"data",dname,f"Data {dname}.{col}")
    for vname,v in cat["views"].items():
        if v.get("system"): add("system",v["system"],"view",vname,f"View {vname} owner")
        if v.get("targetMain"): add("data",v["targetMain"],"view",vname,f"View {vname}")
        for _,val in flatten_view_properties(v.get("properties") or {}):
            if isinstance(val,Mapping) and val.get("data"): add("data",val["data"],"view",vname,f"View {vname} relation")
    for rname,r in cat["ruleviews"].items():
        if r.get("viewRef"): add("view",r["viewRef"],"ruleview",rname,f"RuleView {rname}")
        if r.get("apiRef"): add("api",r["apiRef"],"ruleview",rname,f"RuleView {rname}")
        if r.get("dataSource"): add("datasource",r["dataSource"],"ruleview",rname,f"RuleView {rname}")
        for rr in as_list(r.get("rules")):
            if isinstance(rr,Mapping) and rr.get("dataSource"): add("datasource",rr["dataSource"],"ruleview",rname,f"Rule {rname}.{rr.get('name')}")
    for akey,a in cat["apis"].items():
        if a.get("system"): add("system",a["system"],"api",akey,f"API {akey}")
        resp=a.get("response")
        if isinstance(resp,Mapping) and resp.get("modelRef"): add("view",resp["modelRef"],"api",akey,f"API {akey} response")
        req=a.get("request")
        if isinstance(req,Mapping):
            for par in as_list(req.get("params")):
                if isinstance(par,Mapping) and par.get("relEnum"): add("enum",par["relEnum"],"api",akey,f"API {akey}.{par.get('name')}")
        if isinstance(resp,Mapping):
            for field in as_list(resp.get("fields")):
                if isinstance(field,Mapping) and field.get("relEnum"): add("enum",field["relEnum"],"api",akey,f"API {akey} response.{field.get('name')}")
    for sname,system in cat["systems"].items():
        for d in as_list(system.get("dataRefs")):
            n=d.get("name") if isinstance(d,Mapping) else d
            if n:add("data",n,"system",sname,f"System {sname}")
        for v in as_list(system.get("viewRefs")):
            n=v.get("name") if isinstance(v,Mapping) else v
            if n:add("view",n,"system",sname,f"System {sname}")
        for info in as_list(system.get("information")):
            if isinstance(info,Mapping) and info.get("name"):
                ikey=f"{sname}.{info['name']}"
                if info.get("viewRef"): add("view",info["viewRef"],"info",ikey,f"Information {ikey}")
                if info.get("ruleRef"): add("ruleview",info["ruleRef"],"info",ikey,f"Information {ikey}")
    for b in cat["businesses"]:
        for d in as_list(b.get("directories")):
            if not isinstance(d,Mapping): continue
            dname=d.get("name")
            if d.get("modelRef"): add("view",d["modelRef"],"directory",dname,f"Directory {dname}")
            for a in as_list(d.get("actions")):
                if not isinstance(a,Mapping): continue
                if a.get("systemRef"): add("system",a["systemRef"],"directory",dname,f"Directory {dname} / Action {a.get('name')}")
                if a.get("ruleRef"): add("ruleview",a["ruleRef"],"directory",dname,f"Directory {dname} / Action {a.get('name')}")
    return out


def refs_html(pages: Mapping[str,str], items: Sequence[Tuple[str,str,str]]) -> str:
    if not items:
        return "<span class='muted'>No references in loaded design slice.</span>"
    seen=set(); out=[]
    for kind,name,label_text in items:
        key=(kind,name,label_text)
        if key in seen: continue
        seen.add(key); out.append(f"<li>{link(pages,kind,name,label_text)}</li>")
    return "<ul>"+"".join(out)+"</ul>"

def render_index(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]]) -> str:
    parts=[html_start("DEC Design HTML","所有设计 HTML 由一个脚本从 canonical YAML 确定性生成。Data / View / RuleView / API / Enum / Information 以 System 为首要导航入口。",pages,"index")]
    parts.append("<div class='panel'><h2>Start from System</h2><p class='section-note'>先选择 System，再在该 System 内查看 View → RuleView、Enum、API 与 Information；这是设计站点的主导航路径。</p><div class='card-grid'>")
    for name,system in sorted(cat["systems"].items()):
        views=system_view_entries(cat,name); enums=system_enum_names(cat,name); datas=system_data_names(cat,name)
        apis=[k for k,a in cat["apis"].items() if str(a.get("system") or "")==name]
        infos=[k for k,i in cat["infos"].items() if str(i.get("system") or "")==name]
        rvs=sorted({rn for rn in cat["ruleviews"] if name in ruleview_systems(cat,rn)})
        parts.append(f"<a class='card' style='text-decoration:none;color:inherit' href='{h(pages['system:'+name])}'><h2>{h(name)}</h2><p>{h(system.get('desc') or system.get('description') or '')}</p><div class='meta'><span class='badge'>Data {len(datas)}</span><span class='badge'>Views {len(views)}</span><span class='badge'>RuleViews {len(rvs)}</span><span class='badge'>Enums {len(enums)}</span><span class='badge'>APIs {len(apis)}</span><span class='badge'>Information {len(infos)}</span></div></a>")
    parts.append("</div></div>")
    counts=[("System",len(cat["systems"]),pages["systems"]),("Directory",sum(len(as_list(b.get("directories"))) for b in cat["businesses"]),pages["business"]),("DataSource",len(cat["datasources"]),pages["datasources"]),("Data",len(cat["datas"]),pages["data"])]
    parts.append("<div class='panel'><h2>Global technical indexes</h2><div class='card-grid'>"+"".join(f"<a class='card' style='text-decoration:none;color:inherit' href='{h(p)}'><h3>{h(k)}</h3><div style='font-size:30px;font-weight:750'>{n}</div></a>" for k,n,p in counts)+"</div></div>")
    return "".join(parts)+html_end(docs)


def render_datasources(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC DataSource","DataSource / Connection 设计以及 Data、RuleView、Rule 对数据源的引用。",pages,"datasources"),"<div class='card-grid'>"]
    for name,ds in sorted(cat["datasources"].items()):
        default=" <span class='badge em'>default</span>" if name==cat.get("defaultDataSource") else ""
        rows=[("Type",ds.get("type")),("Description",ds.get("description") or "—"),("URL",ds.get("url") or "—"),("Driver",ds.get("driverClass") or "—")]
        parts.append(f"<article class='card' id='{h(anchor('datasource',name))}'><h3>{h(name)}{default}</h3><dl class='kv'>"+"".join(f"<dt>{h(k)}</dt><dd>{h(v)}</dd>" for k,v in rows)+"</dl><h4>Referenced by</h4>{refs_html(pages,backs['datasource'].get(name,[]))}</article>")
    parts.append("</div><div class='panel'><h2>Connections</h2><div class='card-grid'>")
    for name,c in sorted(cat["connections"].items()):
        dss=[]
        for d in as_list(c.get("dataSources")):
            n=(d.get("ref") or d.get("name")) if isinstance(d,Mapping) else d
            if n:dss.append(link(pages,"datasource",n))
        parts.append(f"<article class='card'><h3>{h(name)}</h3><div>{' · '.join(dss) or '—'}</div></article>")
    parts.append("</div></div>")
    return "".join(parts)+html_end(docs)


def flatten_view_properties(props: Mapping[str,Any], prefix: str="") -> List[Tuple[str,Any]]:
    out=[]
    for name,val in props.items():
        path=f"{prefix}.{name}" if prefix else str(name); out.append((path,val))
        if isinstance(val,Mapping) and isinstance(val.get("properties"),Mapping):out.extend(flatten_view_properties(val["properties"],path))
    return out


def render_data(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC Data","Data 模型、System ownership、Property 类型、Table/Column、DataSource 与 Enum 绑定。",pages,"data")]
    for name,d in sorted(cat["datas"].items()):
        parts.append(f"<article class='card' id='{h(anchor('data',name))}'><h2>{h(name)}</h2><p>{h(d.get('desc') or d.get('description') or '')}</p><dl class='kv'><dt>Design ID</dt><dd>{h(d.get('id') or '—')}</dd><dt>System</dt><dd>{link(pages,'system',d.get('system'))}</dd></dl><h3>Properties</h3><div class='table-wrap'><table><thead><tr><th>Property</th><th>Logical type</th><th>Description</th><th>Enum</th></tr></thead><tbody>")
        for prop,raw in (d.get("properties") or {}).items():
            typ=raw.get("type") if isinstance(raw,Mapping) else raw; desc=raw.get("desc") if isinstance(raw,Mapping) else ""; enums=sorted(_data_property_enums(cat,name,str(prop)))
            en=" · ".join(link(pages,"enum",e) for e in enums) if enums else "—"
            parts.append(f"<tr><td><code>{h(prop)}</code></td><td>{h(typ or '—')}</td><td>{h(desc or '')}</td><td>{en}</td></tr>")
        parts.append("</tbody></table></div><h3>External mappings</h3><div class='table-wrap'><table><thead><tr><th>Table</th><th>DataSource</th><th>Column</th><th>Property</th><th>Source type</th><th>Enum</th></tr></thead><tbody>")
        for t in as_list(d.get("tables")):
            if not isinstance(t,Mapping):continue
            ds=link(pages,"datasource",t.get("dataSource")) if t.get("dataSource") else "—"; cols=t.get("columns") if isinstance(t.get("columns"),Mapping) else {}
            for col,val in cols.items():
                if isinstance(val,Mapping): ref=val.get("ref",val.get("refProperty"));typ=val.get("type") or "—"; en=link(pages,"enum",val.get("relEnum")) if val.get("relEnum") else "—"
                else: ref=val;typ="—";en="—"
                parts.append(f"<tr><td>{h(t.get('name'))}</td><td>{ds}</td><td>{h(col)}</td><td>{h(ref)}</td><td>{h(typ)}</td><td>{en}</td></tr>")
        parts.append("</tbody></table></div><h4>Referenced by</h4>"+refs_html(pages,backs["data"].get(name,[]))+"</article>")
    return "".join(parts)+html_end(docs)

def render_views(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC View","View 页面按对象层级展示 Property / Relation，并从 Data 解析真实逻辑类型与 Enum；View property 可增加 desc。",pages,"views")]
    for name,v in sorted(cat["views"].items()):
        parts.append(f"<article class='card' id='{h(anchor('view',name))}'><h2>{h(name)}</h2><dl class='kv'><dt>Design ID</dt><dd>{h(v.get('id') or '—')}</dd><dt>System</dt><dd>{link(pages,'system',v.get('system'))}</dd><dt>Main Data</dt><dd>{link(pages,'data',v.get('targetMain'))}</dd></dl>")
        rows=view_property_rows(cat,v)
        parts.append("<div class='table-wrap'><table><thead><tr><th>Property / child object</th><th>Kind</th><th>Data mapping</th><th>Type</th><th>Description</th><th>Enum</th><th>Relation</th></tr></thead><tbody>")
        for r in rows:
            indent="&nbsp;"*(r['depth']*6); label=f"{indent}<strong>{h(r['path'].split('.')[-1])}</strong><div class='small muted'>{h(r['path'])}</div>"
            if r['kind']=='relation': mapping=link(pages,'data',r['data']); rel=f"{h(r['relation'])}<br><span class='small'>key={h(r['key'])} · relKey={h(r['relKey'])}</span>"; enum='—'
            else: mapping=f"{link(pages,'data',r['data'])}.<code>{h(r['ref'])}</code>"; rel='—'; enum=link(pages,'enum',r['enum']) if r.get('enum') and ' / ' not in str(r['enum']) else h(r.get('enum') or '—')
            parts.append(f"<tr><td>{label}</td><td>{h(r['kind'])}</td><td>{mapping}</td><td><strong>{h(r['type'])}</strong></td><td>{h(r['desc'] or '')}</td><td>{enum}</td><td>{rel}</td></tr>")
        parts.append("</tbody></table></div><p class='section-note'>标量字段 Type 来自其引用的 Data property；Enum 来自 Data Column.relEnum，因此 View 页面不会维护第二份类型/枚举事实。</p><h4>Referenced by</h4>"+refs_html(pages,backs["view"].get(name,[]))+"</article>")
    return "".join(parts)+html_end(docs)

def render_ruleviews(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC RuleView / rule-view-info","RuleView 默认 DataSource、Rule 覆盖、View/API 绑定与规则定义。",pages,"ruleviews")]
    for name,r in sorted(cat["ruleviews"].items()):
        parts.append(f"<article class='card' id='{h(anchor('ruleview',name))}'><h2>{h(name)}</h2><p>{h(r.get('desc') or '')}</p><dl class='kv'><dt>Design ID</dt><dd>{h(r.get('id') or '—')}</dd><dt>code</dt><dd>{h(r.get('code') or '—')}</dd><dt>Systems</dt><dd>{' '.join(link(pages,'system',x) for x in sorted(ruleview_systems(cat,name))) or '—'}</dd><dt>View</dt><dd>{link(pages,'view',r.get('viewRef'))}</dd><dt>API</dt><dd>{link(pages,'api',r.get('apiRef')) if r.get('apiRef') else '—'}</dd><dt>Default DataSource</dt><dd>{link(pages,'datasource',r.get('dataSource')) if r.get('dataSource') else 'runtime/project default'}</dd></dl><div class='table-wrap'><table><thead><tr><th>Rule</th><th>Type</th><th>DataSource</th><th>Definition</th></tr></thead><tbody>")
        for rr in as_list(r.get("rules")):
            if not isinstance(rr,Mapping):continue
            eff=rr.get("dataSource") or r.get("dataSource"); defn=rr.get("pattern") or rr.get("process") or rr.get("property") or rr.get("cmd") or "—"
            parts.append(f"<tr><td>{h(rr.get('name'))}<div class='small muted'>{h(rr.get('id') or '')}</div></td><td>{h(rr.get('type'))}</td><td>{link(pages,'datasource',eff) if eff else 'runtime/project default'}</td><td><div class='code'>{h(defn)}</div></td></tr>")
        parts.append("</tbody></table></div><h4>Referenced by</h4>"+refs_html(pages,backs["ruleview"].get(name,[]))+"</article>")
    return "".join(parts)+html_end(docs)


def validation_text(v: Mapping[str,Any]) -> str:
    typ=v.get("type"); val=v.get("value") if "value" in v else v.get("values") if "values" in v else v.get("expression")
    return f"{typ}"+(f" = {val}" if val not in (None,"") else "")+(f" ({v.get('message')})" if v.get("message") else "")


def render_apis(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC API","HTTP 接口契约：required、min/max、length、regex、enum/relEnum、expression 与 response。",pages,"apis")]
    for key,a in sorted(cat["apis"].items()):
        parts.append(f"<article class='card' id='{h(anchor('api',key))}'><h2>{h(key)}</h2><p>{h(a.get('desc') or '')}</p><dl class='kv'><dt>Design ID</dt><dd>{h(a.get('id') or '—')}</dd><dt>System</dt><dd>{link(pages,'system',a.get('system'))}</dd><dt>Endpoint</dt><dd><span class='badge em'>{h(a.get('method'))}</span> <code>{h(a.get('url'))}</code></dd></dl>")
        req=a.get("request") if isinstance(a.get("request"),Mapping) else {}; parts.append("<h3>Request</h3><div class='table-wrap'><table><thead><tr><th>Param</th><th>In</th><th>Type</th><th>Required</th><th>Enum</th><th>Validations</th></tr></thead><tbody>")
        for p in as_list(req.get("params")):
            if not isinstance(p,Mapping):continue
            vals="<br>".join(h(validation_text(v)) for v in as_list(p.get("validations")) if isinstance(v,Mapping)) or "—"; en=link(pages,"enum",p.get("relEnum")) if p.get("relEnum") else "—"
            parts.append(f"<tr><td>{h(p.get('name'))}<div class='small muted'>{h(p.get('desc') or '')}</div></td><td>{h(p.get('in'))}</td><td>{h(p.get('type'))}</td><td>{'yes' if p.get('required') else 'no'}</td><td>{en}</td><td>{vals}</td></tr>")
        parts.append("</tbody></table></div>")
        rvals=[v for v in as_list(req.get("validations")) if isinstance(v,Mapping)]
        if rvals:parts.append("<h4>Request-level validations</h4><div class='code'>"+h("\n".join(validation_text(v) for v in rvals))+"</div>")
        resp=a.get("response") if isinstance(a.get("response"),Mapping) else {}; parts.append(f"<h3>Response</h3><dl class='kv'><dt>Type</dt><dd>{h(resp.get('type') or '—')}</dd><dt>Model</dt><dd>{link(pages,'view',resp.get('modelRef')) if resp.get('modelRef') else '—'}</dd><dt>Description</dt><dd>{h(resp.get('desc') or '—')}</dd></dl><h4>Referenced by</h4>{refs_html(pages,backs['api'].get(key,[]))}</article>")
    return "".join(parts)+html_end(docs)


def render_systems(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC System","从 System 进入 View → RuleView、Enum、API 与 Information；点击 System 打开独立设计页。",pages,"systems")]
    parts.append("<div class='panel'><h2>Systems</h2><div class='card-grid'>")
    for name,system in sorted(cat["systems"].items()):
        views=system_view_entries(cat,name); enums=system_enum_names(cat,name); datas=system_data_names(cat,name)
        apis=[k for k,a in cat["apis"].items() if str(a.get("system") or "")==name]
        infos=[k for k,i in cat["infos"].items() if str(i.get("system") or "")==name]
        rv_names=sorted({rn for rn in cat["ruleviews"] if name in ruleview_systems(cat,rn)})
        parts.append(f"<article class='card' id='{h(anchor('system',name))}'><h2>{h(name)}</h2><p>{h(system.get('desc') or system.get('description') or '')}</p>"
                     f"<div class='meta'><span class='badge'>Data {len(datas)}</span><span class='badge'>Views {len(views)}</span><span class='badge'>RuleViews {len(rv_names)}</span><span class='badge'>Enums {len(enums)}</span><span class='badge'>APIs {len(apis)}</span><span class='badge'>Information {len(infos)}</span></div>"
                     f"<p><a class='xref' href='{h(pages['system:'+name])}'>Open {h(name)} system design →</a></p></article>")
    parts.append("</div></div>")
    return "".join(parts)+html_end(docs)


def render_system_detail(system_name: str, cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    system=cat["systems"].get(system_name) or {}
    parts=[html_start(f"DEC System · {system_name}","System 内统一导航 Data / View / RuleView / Enum / API / Information。Data.system 与 View.system 表示归属。",pages,"systems")]
    parts.append(f"<div class='panel'><h2>{h(system_name)}</h2><p>{h(system.get('desc') or system.get('description') or '')}</p><dl class='kv'><dt>Design ID</dt><dd>{h(system.get('id') or '—')}</dd><dt>Information graph</dt><dd><a class='xref' href='information.html'>Open {h(system_name)} Information graph</a></dd><dt>System index</dt><dd><a class='xref' href='{h(pages['systems'])}'>All systems</a></dd></dl></div>")

    # Data list is a first-class System navigation section.
    data_names=system_data_names(cat,system_name)
    parts.append("<div class='panel'><h2>Data</h2>")
    if data_names:
        parts.append("<div class='card-grid'>")
        for dn in data_names:
            d=cat["datas"].get(dn) or {}
            parts.append(f"<article class='card'><h3><a class='xref' href='data.html#{h(anchor('data',dn))}'>{h(dn)}</a></h3><p>{h(d.get('desc') or d.get('description') or '')}</p><div class='meta'><span class='badge'>{h(d.get('id') or '')}</span></div></article>")
        parts.append("</div>")
    else: parts.append("<div class='empty'>No Data belongs to this System.</div>")
    parts.append("</div>")

    # View → RuleView hierarchy.
    view_entries=system_view_entries(cat,system_name)
    parts.append("<div class='panel'><h2>Views → RuleViews</h2>")
    if not view_entries:
        parts.append("<div class='empty'>No View is owned or referenced by this System.</div>")
    else:
        for view_name,mode in view_entries:
            v=cat["views"].get(view_name) or {}; owner=str(v.get("system") or "")
            rv_names=[rn for rn,rv in sorted(cat["ruleviews"].items()) if str(rv.get("viewRef") or "")==view_name and system_name in ruleview_systems(cat,rn)]
            badge="owned" if mode=="owned" else f"referenced · owner={owner or 'unknown'}"
            parts.append(f"<article class='card' id='{h(anchor('system-view',view_name))}'><h3><a class='xref' href='views.html#{h(anchor('view',view_name))}'>{h(view_name)}</a> <span class='badge'>{h(badge)}</span></h3><dl class='kv'><dt>targetMain</dt><dd>{link(pages,'data',v.get('targetMain'))}</dd><dt>RuleViews</dt><dd>{len(rv_names)}</dd></dl>")
            if rv_names:
                parts.append("<ul>")
                for rn in rv_names:
                    rv=cat["ruleviews"][rn]
                    parts.append(f"<li><a class='xref' href='ruleviews.html#{h(anchor('ruleview',rn))}'>{h(rn)}</a> <span class='muted'>{h(rv.get('desc') or '')}</span></li>")
                parts.append("</ul>")
            else:
                parts.append("<div class='muted'>No RuleView associated with this System for this View.</div>")
            parts.append("</article>")
    parts.append("</div>")

    # Enum list with concrete values.
    enums=system_enum_names(cat,system_name)
    parts.append("<div class='panel'><h2>Enums</h2>")
    if enums:
        parts.append("<div class='card-grid'>")
        for ename in enums:
            enum=cat["enums"].get(ename) or {}; vals=[]
            for ev in as_list(enum.get("values")):
                if isinstance(ev,Mapping): vals.append(f"{ev.get('value')}={ev.get('name')}")
            parts.append(f"<article class='card'><h3><a class='xref' href='enums.html#{h(anchor('enum',ename))}'>{h(ename)}</a></h3><p>{h(enum.get('desc') or '')}</p><div class='code'>{h(' · '.join(vals) or '—')}</div></article>")
        parts.append("</div>")
    else: parts.append("<div class='empty'>No Enum associated with this System.</div>")
    parts.append("</div>")

    # API list.
    apis=[(k,a) for k,a in sorted(cat["apis"].items()) if str(a.get("system") or "")==system_name]
    parts.append("<div class='panel'><h2>APIs</h2>")
    if apis:
        parts.append("<div class='table-wrap'><table><thead><tr><th>API</th><th>Method</th><th>URL</th><th>Description</th></tr></thead><tbody>")
        for key,a in apis:
            parts.append(f"<tr><td><a class='xref' href='apis.html#{h(anchor('api',key))}'>{h(key)}</a></td><td>{h(a.get('method'))}</td><td><code>{h(a.get('url'))}</code></td><td>{h(a.get('desc') or '')}</td></tr>")
        parts.append("</tbody></table></div>")
    else: parts.append("<div class='empty'>No API belongs to this System.</div>")
    parts.append("</div>")

    # Information list.
    infos=[(k,i) for k,i in sorted(cat["infos"].items()) if str(i.get("system") or "")==system_name]
    parts.append("<div class='panel'><h2>Information</h2>")
    if infos:
        parts.append("<div class='table-wrap'><table><thead><tr><th>Information</th><th>Recognizer</th><th>View</th><th>RuleView</th><th>Description</th></tr></thead><tbody>")
        for key,info in infos:
            parts.append(f"<tr><td><a class='xref' href='information.html#{h(anchor('info',key))}'>{h(key)}</a></td><td>{h(recognizer(info))}</td><td>{link(pages,'view',info.get('viewRef')) if info.get('viewRef') else '—'}</td><td>{link(pages,'ruleview',info.get('ruleRef')) if info.get('ruleRef') else '—'}</td><td>{h(info.get('description') or info.get('desc') or '')}</td></tr>")
        parts.append("</tbody></table></div>")
    else: parts.append("<div class='empty'>No Information belongs to this System.</div>")
    parts.append("</div>")

    # Supporting Data and ModelAccess remain visible but secondary.
    parts.append("<div class='panel'><h2>Supporting Data / ModelAccess</h2><h3>Data</h3><div class='ref-list'>")
    for raw in as_list(system.get("dataRefs")):
        name=raw.get("name") if isinstance(raw,Mapping) else raw
        if name:parts.append(link(pages,"data",name))
    parts.append("</div><h3>ModelAccess</h3><div class='table-wrap'><table><thead><tr><th>Model/View</th><th>Read</th><th>Write</th></tr></thead><tbody>")
    for ma in as_list(system.get("modelAccess")):
        if not isinstance(ma,Mapping):continue
        reads=", ".join(str(x.get("path")) for x in as_list(ma.get("read")) if isinstance(x,Mapping)); writes=", ".join(str(x.get("path")) for x in as_list(ma.get("write")) if isinstance(x,Mapping))
        parts.append(f"<tr><td>{link(pages,'view',ma.get('modelRef'))}</td><td>{h(reads or '—')}</td><td>{h(writes or '—')}</td></tr>")
    parts.append("</tbody></table></div><h3>Referenced by</h3>"+refs_html(pages,backs["system"].get(system_name,[]))+"</div>")
    return "".join(parts)+html_end(docs)

def render_enums(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC Enum","公共枚举是 API relEnum、Data Column relEnum 与 Information changeData 的统一事实源。",pages,"enums")]
    for name,e in sorted(cat["enums"].items()):
        systems=[s for s in sorted(cat["systems"]) if name in system_enum_names(cat,s)]
        parts.append(f"<article class='card' id='{h(anchor('enum',name))}'><h2>{h(name)}</h2><p>{h(e.get('desc') or '')}</p><dl class='kv'><dt>Design ID</dt><dd>{h(e.get('id') or '—')}</dd><dt>Systems</dt><dd>{''.join(link(pages,'system',x)+' ' for x in systems) if systems else '—'}</dd></dl><div class='table-wrap'><table><thead><tr><th>Value</th><th>Name</th><th>Description</th></tr></thead><tbody>")
        for v in as_list(e.get("values")):
            if isinstance(v,Mapping):parts.append(f"<tr><td><strong>{h(v.get('value'))}</strong></td><td>{h(v.get('name'))}</td><td>{h(v.get('desc') or '')}</td></tr>")
        parts.append("</tbody></table></div>")
        changes=[]
        for ikey,info in sorted(cat["infos"].items()):
            for item in information_enum_changes(cat,info):
                if item.get("enum")==name:changes.append((ikey,info,item))
        if changes:
            parts.append("<h3>Information change mappings</h3><p class='section-note'>Information.changeData 的具体值必须存在于本 Enum；validator 会进行一致性校验。</p><div class='table-wrap'><table><thead><tr><th>Information</th><th>Property</th><th>Enum value</th><th>Meaning</th></tr></thead><tbody>")
            for ikey,info,item in changes:
                ev=item.get("enumValue") or {}
                parts.append(f"<tr><td>{link(pages,'info',ikey)}</td><td><code>{h(item.get('property'))}</code></td><td><strong>{h(item.get('value'))}</strong></td><td>{h(ev.get('name') or '—')} · {h(ev.get('desc') or '')}</td></tr>")
            parts.append("</tbody></table></div>")
        parts.append("<h4>Referenced by</h4>"+refs_html(pages,backs["enum"].get(name,[]))+"</article>")
    return "".join(parts)+html_end(docs)

def write_site(out: Path, docs: Sequence[Tuple[Path,Mapping[str,Any]]]) -> Dict[str,Path]:
    # Prefer a site directory. Legacy .html arguments select the parent directory;
    # shared page names no longer inherit any particular business/file prefix.
    legacy_stem = out.stem if out.suffix.lower() == ".html" else None
    site_root = out.parent if legacy_stem is not None else out
    out = site_root / "directories.html"
    cat=build_catalog(docs); validate_visual_semantics(cat); systems=sorted(cat["systems"]); pages=page_names(out,systems)
    for kind, names in (("Business", [str(b.get("name") or "business") for b in cat["businesses"]]),
                        ("System", systems)):
        paths = set()
        for name in names:
            path = slug(name)
            if path in {".", ".."} or path in paths:
                raise ValueError(f"{kind} HTML filename collision or invalid name: {name}")
            paths.add(path)
    for b in cat["businesses"]:
        bname=str(b.get("name") or "business")
        for d in as_list(b.get("directories")):
            if isinstance(d,Mapping) and d.get("name"): pages[f"directory:{d['name']}"]=f"directory/{slug(bname)}.html#{anchor('directory',d['name'])}"
    backs=backlink_lists(cat); out.parent.mkdir(parents=True,exist_ok=True)
    cleanup_legacy_system_directories(out.parent, systems)
    generated:Dict[str,Path]={}
    def wpath(key: str, rel: str, text: str) -> None:
        p=out.parent/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text,encoding="utf-8"); generated[key]=p
    # Root indexes / compatibility technical indexes.
    wpath("business",pages["business"],render_business_html(cat,pages,docs)); wpath("index",pages["index"],render_index(cat,pages,docs)); wpath("datasources",pages["datasources"],render_datasources(cat,pages,docs,backs)); wpath("data",pages["data"],render_data(cat,pages,docs,backs)); wpath("views",pages["views"],render_views(cat,pages,docs,backs)); wpath("ruleviews",pages["ruleviews"],render_ruleviews(cat,pages,docs,backs)); wpath("apis",pages["apis"],render_apis(cat,pages,docs,backs)); wpath("systems",pages["systems"],render_systems(cat,pages,docs,backs)); wpath("enums",pages["enums"],render_enums(cat,pages,docs,backs))
    # All System directories are isolated under system/ so a System name can never
    # collide with reserved site directories such as directory/.
    system_root=out.parent/"system"
    system_root.mkdir(parents=True,exist_ok=True)
    for system in systems:
        local_pages=subdir_pages(pages,system); sc=system_slice(cat,system); apply_system_entity_routes(local_pages,cat,system,sc); sbacks=backlink_lists(sc); base=f"system/{slug(system)}"
        wpath(f"system:{system}",f"{base}/index.html",render_system_detail(system,cat,local_pages,docs,backs))
        wpath(f"system-data:{system}",f"{base}/data.html",render_data(sc,local_pages,docs,sbacks))
        wpath(f"system-views:{system}",f"{base}/views.html",render_views(sc,local_pages,docs,sbacks))
        wpath(f"system-ruleviews:{system}",f"{base}/ruleviews.html",render_ruleviews(sc,local_pages,docs,sbacks))
        wpath(f"system-apis:{system}",f"{base}/apis.html",render_apis(sc,local_pages,docs,sbacks))
        wpath(f"system-enums:{system}",f"{base}/enums.html",render_enums(sc,local_pages,docs,sbacks))
        wpath(f"info:{system}",f"{base}/information.html",render_information_html(system,cat,local_pages,docs))
    # Business Directory Map detail pages live under directory/.
    dp=subdir_pages(pages,None)
    for b in cat["businesses"]:
        name=str(b.get("name") or "business"); bpages=dict(dp)
        for d in as_list(b.get("directories")):
            if isinstance(d,Mapping) and d.get("name"): bpages[f"directory:{d['name']}"]=f"{slug(name)}.html#{anchor('directory',d['name'])}"
        wpath(f"business-map:{name}",f"directory/{slug(name)}.html",render_business_map_detail(b,cat,bpages,docs))
    cleanup_legacy_root_pages(site_root, pages, [legacy_stem] +
                              [str(b.get("name") or "business") for b in cat["businesses"]])
    return generated


def main(argv: Sequence[str] | None = None) -> int:
    parser=argparse.ArgumentParser(description="Generate DEC Business/Information graphs and linked HTML design documents from canonical YAML")
    parser.add_argument("inputs",nargs="+",help="canonical DEC YAML file(s) or directories")
    parser.add_argument("-o","--output",required=True,help="HTML site output directory (recommended); legacy .html paths select their parent directory. Entry pages: index.html and directories.html")
    args=parser.parse_args(argv)
    try:
        docs=load_docs(discover(args.inputs)); out=Path(args.output); generated=write_site(out,docs)
        print(f"PASSED: rendered html_files={len(generated)} index={generated['index']} business={generated['business']} from {len(docs)} YAML document(s); existing generated HTML overwritten")
        return 0
    except Exception as e:
        print(f"FAILED: {e}",file=sys.stderr);return 2


if __name__ == "__main__":
    raise SystemExit(main())
