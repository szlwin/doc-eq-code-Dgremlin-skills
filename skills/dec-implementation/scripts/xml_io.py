#!/usr/bin/env python3
"""Read/check derived DEC XML without ad-hoc file access.

This implementation Skill never writes DEC XML. XML write/regeneration belongs
at the design/converter boundary. This tool is only for safe derived-artifact
inspection when XML is supplied as secondary evidence.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence
from xml.dom import Node, minidom


def _xml_files(path: Path) -> List[Path]:
    if path.is_file():
        if path.suffix.lower() != ".xml":
            raise ValueError(f"not an XML file: {path}")
        return [path]
    if not path.is_dir():
        raise ValueError(f"path does not exist: {path}")
    return sorted(p for p in path.rglob("*.xml") if p.is_file())


def _element(el) -> Dict[str, Any]:
    out: Dict[str, Any] = {
        "tag": el.tagName,
        "attributes": {a.name: a.value for a in el.attributes.values()},
        "children": [],
    }
    text: List[str] = []
    for child in el.childNodes:
        if child.nodeType == Node.ELEMENT_NODE:
            out["children"].append(_element(child))
        elif child.nodeType in {Node.TEXT_NODE, Node.CDATA_SECTION_NODE} and child.data.strip():
            text.append(child.data.strip())
    if text:
        out["text"] = "\n".join(text)
    return out


def parse(path: Path) -> Dict[str, Any]:
    doc = minidom.parse(str(path))
    return _element(doc.documentElement)


def main(argv: Optional[Sequence[str]] = None) -> int:
    p = argparse.ArgumentParser(description="Script-only derived DEC XML read/check")
    sub = p.add_subparsers(dest="command", required=True)
    r = sub.add_parser("read")
    r.add_argument("path", type=Path)
    c = sub.add_parser("check")
    c.add_argument("path", type=Path)
    args = p.parse_args(argv)
    try:
        files = _xml_files(args.path.resolve())
        if args.command == "check":
            for f in files:
                minidom.parse(str(f))
            print(f"PASSED: xml_files={len(files)}")
            return 0
        if args.path.is_file():
            payload: Any = parse(files[0])
        else:
            root = args.path.resolve()
            payload = {"root": str(root), "files": [{"path": str(f.relative_to(root)), "document": parse(f)} for f in files]}
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
