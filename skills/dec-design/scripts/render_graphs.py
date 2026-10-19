"""DEC HTML graphs rendering responsibilities."""
from __future__ import annotations
import json
import re
import unicodedata
from collections import defaultdict, deque
from typing import Any, Dict, List, Mapping, Tuple
from render_common import (
    _defs, _edge_path, _interactive_group, _svg_text_lines, anchor, as_list, h, link
)
from render_catalog import system_information_context
from render_details import (
    action_data_sources, action_detail_html, directory_detail_html, info_detail_html, information_inline_html, recognizer
)


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
