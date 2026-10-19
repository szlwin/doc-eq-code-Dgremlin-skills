"""DEC HTML pages rendering responsibilities."""
from __future__ import annotations
from pathlib import Path
from typing import Any, Mapping, Sequence, Tuple
from dec_common import data_property_enums
from render_catalog import (
    information_enum_changes, ruleview_systems, system_data_names, system_enum_names, system_view_entries, view_property_rows
)
from render_common import (
    anchor, as_list, h, html_end, html_start, link, slug, toolbar
)
from render_details import (
    action_detail_html, info_detail_html, recognizer
)
from render_graphs import (
    article_business_graph, article_information_graph, system_information_context
)


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


def render_data(cat: Mapping[str,Any], pages: Mapping[str,str], docs: Sequence[Tuple[Path,Mapping[str,Any]]], backs: Mapping[str,Any]) -> str:
    parts=[html_start("DEC Data","Data 模型、System ownership、Property 类型、Table/Column、DataSource 与 Enum 绑定。",pages,"data")]
    for name,d in sorted(cat["datas"].items()):
        parts.append(f"<article class='card' id='{h(anchor('data',name))}'><h2>{h(name)}</h2><p>{h(d.get('desc') or d.get('description') or '')}</p><dl class='kv'><dt>Design ID</dt><dd>{h(d.get('id') or '—')}</dd><dt>System</dt><dd>{link(pages,'system',d.get('system'))}</dd></dl><h3>Properties</h3><div class='table-wrap'><table><thead><tr><th>Property</th><th>Logical type</th><th>Description</th><th>Enum</th></tr></thead><tbody>")
        for prop,raw in (d.get("properties") or {}).items():
            typ=raw.get("type") if isinstance(raw,Mapping) else raw; desc=raw.get("desc") if isinstance(raw,Mapping) else ""; enums=sorted(data_property_enums(cat["datas"],name,str(prop)))
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
