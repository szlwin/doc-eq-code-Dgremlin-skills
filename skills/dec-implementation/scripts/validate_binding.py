#!/usr/bin/env python3
"""Validate DEC implementation bindings against canonical DEC YAML design IDs."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "schemas" / "implementation-binding.schema.json"
DESIGN_ID_RE = re.compile(r"^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+$")


def files(path: Path) -> List[Path]:
    if path.is_file():
        return [path]
    return sorted(p for p in path.rglob("*") if p.is_file() and p.suffix.lower() in {".yaml", ".yml"})


def load_yaml(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def walk(value: Any, location: str = "root") -> Iterable[Tuple[str, Mapping[str, Any]]]:
    if isinstance(value, Mapping):
        yield location, value
        for k, v in value.items():
            yield from walk(v, f"{location}.{k}")
    elif isinstance(value, list):
        for i, v in enumerate(value):
            yield from walk(v, f"{location}[{i}]")


def collect_design_ids(path: Path) -> Tuple[Set[str], List[str]]:
    ids: Set[str] = set()
    errors: List[str] = []
    seen: Dict[str, str] = {}
    for file in files(path):
        try:
            doc = load_yaml(file)
        except Exception as exc:
            errors.append(f"{file}: cannot parse YAML: {exc}")
            continue
        if isinstance(doc, Mapping):
            artifact = doc.get("artifact")
            if (
                isinstance(artifact, Mapping)
                and (
                    artifact.get("producer") == "html-recovery"
                    or any(key in doc for key in ("sourceText", "source_rows", "informationTree"))
                )
            ):
                errors.append(
                    f"{file}: HTML recovery artifact is not canonical DEC YAML; "
                    "provide an approved kind: document before creating a binding"
                )
                continue
        for loc, node in walk(doc):
            if node.get("id") is None:
                continue
            ident = str(node["id"])
            # Only stable DEC Design IDs are traceability identities. Values such
            # as a Data property named "id" mapping to type "int", or a View
            # property mapping to ref "id", are ordinary business data and must
            # never enter the Design-ID index.
            if not DESIGN_ID_RE.match(ident):
                continue
            here = f"{file}:{loc}"
            if ident in seen:
                errors.append(f"duplicate design id {ident}: {seen[ident]} and {here}")
            else:
                seen[ident] = here
                ids.add(ident)
    return ids, errors


def location_exists(project_root: Optional[Path], loc: Mapping[str, Any]) -> Optional[bool]:
    if project_root is None or not loc.get("file"):
        return None
    return (project_root / str(loc["file"])).exists()


def main(argv: Optional[Sequence[str]] = None) -> int:
    p = argparse.ArgumentParser(description="Validate implementation-binding.yaml against DEC design IDs")
    p.add_argument("--design", required=True, type=Path, help="canonical DEC YAML file or directory")
    p.add_argument("--binding", required=True, type=Path)
    p.add_argument("--project-root", type=Path, help="optional source project root; validates bound file paths")
    p.add_argument("--require-tests", action="store_true", help="require tests for IMPLEMENTED/VERIFIED non-runtime bindings")
    p.add_argument("--require-complete", action="store_true", help="require every canonical Design ID to have one binding")
    p.add_argument("--require-ready", action="store_true", help="require every canonical Design ID to have a VERIFIED binding")
    p.add_argument("--json", action="store_true")
    args = p.parse_args(argv)

    result: Dict[str, Any] = {"status": "PASSED", "errors": [], "warnings": []}
    try:
        design_ids, id_errors = collect_design_ids(args.design.resolve())
        result["errors"].extend(id_errors)

        binding = load_yaml(args.binding.resolve())
        schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        validator = jsonschema.Draft202012Validator(schema)
        for err in sorted(validator.iter_errors(binding), key=lambda e: list(e.path)):
            result["errors"].append(f"binding schema {list(err.path)}: {err.message}")

        seen: Set[str] = set()
        bound_ids: Set[str] = set()
        if isinstance(binding, Mapping):
            for i, item in enumerate(binding.get("bindings") or []):
                if not isinstance(item, Mapping):
                    continue
                design_id = str(item.get("designId", ""))
                if not design_id:
                    continue
                if design_id in seen:
                    result["errors"].append(f"duplicate binding for designId {design_id}")
                seen.add(design_id)
                bound_ids.add(design_id)
                if design_id not in design_ids:
                    result["errors"].append(f"binding references unknown designId {design_id}")

                runtime = bool(item.get("runtimeManaged"))
                implementations = item.get("implementation") or []
                status = item.get("status")
                if args.require_ready and status != "VERIFIED":
                    result["errors"].append(f"{design_id}: release readiness requires VERIFIED, found {status}")
                if status in {"IMPLEMENTED", "VERIFIED"} and not runtime and not implementations:
                    result["errors"].append(f"{design_id}: {status} requires implementation locations or runtimeManaged=true")
                if args.require_tests and status in {"IMPLEMENTED", "VERIFIED"} and not runtime and not (item.get("tests") or []):
                    result["errors"].append(f"{design_id}: tests required")

                if args.project_root:
                    root = args.project_root.resolve()
                    for group in ("implementation", "tests"):
                        for loc in item.get(group) or []:
                            exists = location_exists(root, loc)
                            if exists is False:
                                result["errors"].append(f"{design_id}: bound {group} file does not exist: {loc.get('file')}")

        if args.require_complete or args.require_ready:
            for missing in sorted(design_ids - bound_ids):
                result["errors"].append(f"missing binding for designId {missing}")

        result["designIds"] = len(design_ids)
        result["boundIds"] = len(bound_ids)
        if result["errors"]:
            result["status"] = "FAILED"
    except Exception as exc:
        result["status"] = "FAILED"
        result["errors"].append(str(exc))

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"{result['status']}: designIds={result.get('designIds', 0)} boundIds={result.get('boundIds', 0)}")
        for warning in result["warnings"]:
            print(f"WARN {warning}")
        for error in result["errors"]:
            print(f"ERROR {error}", file=sys.stderr)
    return 0 if result["status"] == "PASSED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
