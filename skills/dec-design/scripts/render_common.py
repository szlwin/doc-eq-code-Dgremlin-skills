"""DEC HTML common rendering responsibilities."""
from __future__ import annotations
import html
import re
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Sequence, Tuple

ASSET_ROOT = Path(__file__).parent / "render_assets"
CSS = (ASSET_ROOT / "design.css").read_text(encoding="utf-8")
JS = (ASSET_ROOT / "design.js").read_text(encoding="utf-8")


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
