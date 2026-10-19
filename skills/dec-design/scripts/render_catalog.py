"""DEC HTML catalog rendering responsibilities."""
from __future__ import annotations
import sys
from collections import defaultdict, deque
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, MutableMapping, Sequence, Set, Tuple
import yaml
from dec_common import data_property_enums
from dec_common import extract_info_refs, parse_change_assignments, resolve_view_property_enums as resolve_property_enums
from render_common import (
    anchor, as_list, slug
)


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


def resolve_view_property_enums(cat, view_name, path):
    view = cat["views"].get(str(view_name))
    return resolve_property_enums(view, path, cat["datas"]) if isinstance(view, Mapping) else set()


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
    enums=data_property_enums(cat["datas"],data_name,prop_name)
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


def flatten_view_properties(props: Mapping[str,Any], prefix: str="") -> List[Tuple[str,Any]]:
    out=[]
    for name,val in props.items():
        path=f"{prefix}.{name}" if prefix else str(name); out.append((path,val))
        if isinstance(val,Mapping) and isinstance(val.get("properties"),Mapping):out.extend(flatten_view_properties(val["properties"],path))
    return out


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


