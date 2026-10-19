"""DEC HTML details rendering responsibilities."""
from __future__ import annotations
import json
from typing import Any, List, Mapping, Tuple
from render_catalog import (
    information_enum_changes
)
from render_common import (
    as_list, h, link
)


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
