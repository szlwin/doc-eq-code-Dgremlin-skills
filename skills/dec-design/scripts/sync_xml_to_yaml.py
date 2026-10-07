#!/usr/bin/env python3
"""Safely synchronize an externally edited DEC XML file into existing canonical YAML.

This is a controlled *external XML change ingestion* workflow, not dual-master
synchronization. The existing YAML remains authoritative after a successful sync.

Algorithm:
1. Parse existing canonical YAML.
2. Project existing YAML through YAML -> XML -> YAML to discover the fields that
   are XML-representable.
3. Parse the externally edited XML into canonical form.
4. Apply only XML-representable changes to the existing YAML while preserving
   YAML-only metadata.
5. Refuse ambiguous entity deletions/renames unless they are unambiguous via
   stable ``dec-id`` comments or the caller explicitly allows deletions.
6. Validate the merged YAML before/after atomic write; rollback on validator
   failure.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Mapping, MutableMapping, Optional, Sequence, Tuple
from xml.dom import minidom

import yaml

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from xml_to_yaml import convert_document as xml_to_yaml_document, load_xml
from yaml_to_xml import convert_document as yaml_to_xml_document


class SyncError(ValueError):
    pass


class LiteralDumper(yaml.SafeDumper):
    pass


def _str_representer(dumper: yaml.Dumper, value: str):
    style = "|" if "\n" in value else None
    return dumper.represent_scalar("tag:yaml.org,2002:str", value, style=style)


LiteralDumper.add_representer(str, _str_representer)


def _load_yaml(path: Path) -> Dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as fh:
            value = yaml.safe_load(fh)
    except yaml.YAMLError as exc:
        raise SyncError(f"{path}: YAML parse error: {exc}") from exc
    if not isinstance(value, Mapping):
        raise SyncError(f"{path}: YAML root must be mapping")
    return dict(value)


def _dump_yaml(value: Mapping[str, Any]) -> str:
    return yaml.dump(
        dict(value),
        Dumper=LiteralDumper,
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    )


def _atomic_write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


def _path_join(base: str, part: str) -> str:
    return f"{base}.{part}" if base else part


def _list_name(path: str) -> str:
    return path.rsplit(".", 1)[-1].split("[")[0]


def _semantic_key(item: Mapping[str, Any], path: str) -> Optional[Tuple[Any, ...]]:
    """Return a stable semantic key for a list item when possible."""
    name = _list_name(path)
    if name == "apis" and item.get("name") is not None:
        return ("api", item.get("system"), item.get("name"))
    if name == "params" and item.get("name") is not None:
        return ("param", item.get("in"), item.get("name"))
    if name == "values":
        if item.get("name") is not None:
            return ("enum-value", item.get("name"))
        if item.get("value") is not None:
            return ("enum-value-number", item.get("value"))
    if name == "subDirectories" and item.get("rel") is not None:
        return ("subdir", item.get("rel"))
    if name == "dependencies" and item.get("informationRef") is not None:
        return ("dependency", item.get("informationRef"))
    if name == "produces":
        if item.get("ref") is not None:
            return ("produce", item.get("ref"), item.get("informationRef"))
        if item.get("informationRef") is not None:
            return ("produce-info", item.get("informationRef"))
    if name == "validations" and item.get("type") is not None:
        # A validation's value/expression/message can legitimately be edited.
        # Type is the semantic slot within a parameter/request validation list.
        return ("validation", item.get("type"))
    if name.endswith("Files") and item.get("path") is not None:
        return ("file", item.get("path"))
    if item.get("name") is not None:
        return (name, item.get("name"))
    if item.get("path") is not None:
        return (name, item.get("path"))
    if item.get("ref") is not None:
        return (name, item.get("ref"))
    if item.get("informationRef") is not None:
        return (name, item.get("informationRef"))
    if item.get("modelRef") is not None:
        return (name, item.get("modelRef"))
    return None


def _match_index(items: List[Any], needle: Mapping[str, Any], path: str) -> Optional[int]:
    nid = needle.get("id")
    if nid is not None:
        for i, candidate in enumerate(items):
            if isinstance(candidate, Mapping) and candidate.get("id") == nid:
                return i
    key = _semantic_key(needle, path)
    if key is not None:
        matches = [
            i for i, candidate in enumerate(items)
            if isinstance(candidate, Mapping) and _semantic_key(candidate, path) == key
        ]
        if len(matches) == 1:
            return matches[0]
    return None


def _record(report: MutableMapping[str, Any], bucket: str, path: str, **extra: Any) -> None:
    report.setdefault(bucket, []).append({"path": path, **extra})


def _merge_projected(
    full: Any,
    old_projection: Any,
    new_projection: Any,
    *,
    path: str,
    report: MutableMapping[str, Any],
    allow_delete: bool,
) -> Any:
    """Apply old-projection -> new-projection changes to the full canonical value."""
    if isinstance(old_projection, Mapping) and isinstance(new_projection, Mapping):
        base: Dict[str, Any] = dict(full) if isinstance(full, Mapping) else {}
        old_keys = set(old_projection)
        new_keys = set(new_projection)

        for key in sorted(new_keys | old_keys):
            subpath = _path_join(path, str(key))
            if key == "id" and key in base:
                # Canonical Design IDs are YAML identity. XML comments may help
                # matching but may never rewrite an existing YAML Design ID.
                continue
            if key in old_projection and key in new_projection:
                oldv = old_projection[key]
                newv = new_projection[key]
                fullv = base.get(key, copy.deepcopy(oldv))
                base[key] = _merge_projected(
                    fullv, oldv, newv, path=subpath, report=report, allow_delete=allow_delete
                )
            elif key in new_projection:
                base[key] = copy.deepcopy(new_projection[key])
                _record(report, "added", subpath, value=new_projection[key])
            else:
                if key in base:
                    del base[key]
                    _record(report, "removedFields", subpath, old=old_projection[key])
        return base

    if isinstance(old_projection, list) and isinstance(new_projection, list):
        # Scalar lists are fully XML-representable and unambiguous: replace.
        if all(not isinstance(x, Mapping) for x in old_projection + new_projection):
            if old_projection != new_projection:
                _record(report, "updated", path, old=old_projection, new=new_projection)
            return copy.deepcopy(new_projection)

        full_list = list(full) if isinstance(full, list) else copy.deepcopy(old_projection)
        old_to_full: Dict[int, int] = {}
        used_full: set[int] = set()
        for oi, old_item in enumerate(old_projection):
            if not isinstance(old_item, Mapping):
                continue
            fi = _match_index(full_list, old_item, path)
            if fi is not None and fi not in used_full:
                old_to_full[oi] = fi
                used_full.add(fi)

        new_to_old: Dict[int, int] = {}
        used_old: set[int] = set()
        for ni, new_item in enumerate(new_projection):
            if not isinstance(new_item, Mapping):
                continue
            oi = _match_index(old_projection, new_item, path)
            if oi is not None and oi not in used_old:
                new_to_old[ni] = oi
                used_old.add(oi)

        removals = [oi for oi in range(len(old_projection)) if oi not in used_old]
        additions = [ni for ni in range(len(new_projection)) if ni not in new_to_old]

        # A remove+add pair without stable id is very likely a rename. Fail closed.
        if removals and additions:
            for oi in removals:
                old_item = old_projection[oi]
                old_id = old_item.get("id") if isinstance(old_item, Mapping) else None
                _record(
                    report,
                    "conflicts",
                    f"{path}[{oi}]",
                    code="AMBIGUOUS_RENAME_OR_REPLACEMENT",
                    message="entity disappeared while a new entity appeared in the same list; retain dec-id comments to make rename unambiguous",
                    designId=old_id,
                )

        result = list(full_list)
        # Apply updates using original full indices.
        for ni, oi in sorted(new_to_old.items()):
            old_item = old_projection[oi]
            new_item = new_projection[ni]
            fi = old_to_full.get(oi)
            if fi is None:
                _record(report, "conflicts", f"{path}[{oi}]", code="EXISTING_ENTITY_NOT_FOUND")
                continue
            result[fi] = _merge_projected(
                result[fi], old_item, new_item,
                path=f"{path}[{ni}]", report=report, allow_delete=allow_delete,
            )

        # Entity deletions are deliberately gated.
        delete_full_indices: List[int] = []
        for oi in removals:
            fi = old_to_full.get(oi)
            old_item = old_projection[oi]
            if fi is None:
                continue
            if allow_delete:
                delete_full_indices.append(fi)
                _record(report, "removedEntities", f"{path}[{oi}]", old=old_item)
            else:
                _record(
                    report,
                    "conflicts",
                    f"{path}[{oi}]",
                    code="ENTITY_DELETE_REQUIRES_ALLOW_DELETE",
                    message="XML entity deletion is not applied unless --allow-delete is supplied",
                    designId=old_item.get("id") if isinstance(old_item, Mapping) else None,
                )

        for fi in sorted(delete_full_indices, reverse=True):
            del result[fi]

        # Add new entities. IDs synthesized by xml_to_yaml(auto) are retained.
        for ni in additions:
            item = copy.deepcopy(new_projection[ni])
            result.append(item)
            _record(report, "added", f"{path}[+{ni}]", value=item)

        return result

    if old_projection != new_projection:
        _record(report, "updated", path, old=old_projection, new=new_projection)
        return copy.deepcopy(new_projection)
    return copy.deepcopy(full)


def _project_existing(existing: Mapping[str, Any]) -> Dict[str, Any]:
    xml_text = yaml_to_xml_document(existing, emit_id_comments=True)
    return xml_to_yaml_document(minidom.parseString(xml_text), id_mode="comments")


def sync_document(
    existing: Mapping[str, Any], edited_xml: minidom.Document, *, allow_delete: bool = False
) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    old_projection = _project_existing(existing)
    new_projection = xml_to_yaml_document(edited_xml, id_mode="auto")

    old_kind = old_projection.get("kind")
    new_kind = new_projection.get("kind")
    report: Dict[str, Any] = {
        "status": "PENDING",
        "kind": old_kind,
        "updated": [],
        "added": [],
        "removedFields": [],
        "removedEntities": [],
        "conflicts": [],
        "preservedYamlOnly": [],
    }
    if old_kind != new_kind:
        _record(report, "conflicts", "kind", code="KIND_MISMATCH", old=old_kind, new=new_kind)
        report["status"] = "CONFLICT"
        return dict(existing), report

    merged = _merge_projected(
        copy.deepcopy(existing), old_projection, new_projection,
        path="root", report=report, allow_delete=allow_delete,
    )

    # Record YAML-only top-level keys for traceability; nested metadata is preserved
    # by the same projection-aware merge even when not explicitly listed here.
    for key in existing:
        if key not in old_projection:
            _record(report, "preservedYamlOnly", f"root.{key}")

    # Converter validation catches grammar/shape issues before any write.
    try:
        yaml_to_xml_document(merged, emit_id_comments=True)
    except Exception as exc:
        _record(report, "conflicts", "root", code="MERGED_DOCUMENT_INVALID", message=str(exc))

    report["status"] = "CONFLICT" if report["conflicts"] else "READY"
    report["changeCounts"] = {
        "updated": len(report["updated"]),
        "added": len(report["added"]),
        "removedFields": len(report["removedFields"]),
        "removedEntities": len(report["removedEntities"]),
        "conflicts": len(report["conflicts"]),
    }
    return merged, report


def _write_report(path: Optional[Path], report: Mapping[str, Any]) -> None:
    text = json.dumps(report, ensure_ascii=False, indent=2, default=str)
    if path is not None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text + "\n", encoding="utf-8")
    print(text)


def _run_validator(root: Path) -> Tuple[bool, str]:
    script = SCRIPT_DIR / "validate_dec_yaml.py"
    proc = subprocess.run(
        [sys.executable, str(script), str(root)],
        text=True,
        capture_output=True,
    )
    output = (proc.stdout + ("\n" + proc.stderr if proc.stderr else "")).strip()
    return proc.returncode == 0, output


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Safely synchronize externally edited DEC XML into existing canonical YAML")
    parser.add_argument("xml", type=Path, help="externally edited DEC XML file")
    parser.add_argument("--existing", required=True, type=Path, help="existing canonical YAML file to preserve/merge")
    parser.add_argument("-o", "--output", type=Path, help="output YAML; may equal --existing")
    parser.add_argument("--in-place", action="store_true", help="write back to --existing")
    parser.add_argument("--allow-delete", action="store_true", help="allow XML entity deletion to remove matched YAML entities")
    parser.add_argument("--check", action="store_true", help="compute semantic sync and report only; do not write")
    parser.add_argument("--report", type=Path, help="optional JSON sync report path")
    parser.add_argument("--validate-root", type=Path, help="directory/file passed to validate_dec_yaml.py after write")
    args = parser.parse_args(argv)

    try:
        xml_path = args.xml.resolve()
        existing_path = args.existing.resolve()
        if not xml_path.is_file():
            raise SyncError(f"XML input does not exist or is not a file: {xml_path}")
        if not existing_path.is_file():
            raise SyncError(f"existing YAML does not exist or is not a file: {existing_path}")
        if args.in_place and args.output is not None:
            raise SyncError("use either --in-place or --output, not both")
        if not args.check and not args.in_place and args.output is None:
            raise SyncError("write mode requires --in-place or --output")

        existing = _load_yaml(existing_path)
        merged, report = sync_document(existing, load_xml(xml_path), allow_delete=args.allow_delete)
        report.update({
            "xml": str(xml_path),
            "existing": str(existing_path),
            "allowDelete": bool(args.allow_delete),
        })

        if report["status"] == "CONFLICT":
            _write_report(args.report.resolve() if args.report else None, report)
            return 3

        if args.check:
            report["status"] = "PASSED"
            _write_report(args.report.resolve() if args.report else None, report)
            return 0

        target = existing_path if args.in_place else args.output.resolve()
        original_exists = target.exists()
        original_bytes = target.read_bytes() if original_exists else None
        _atomic_write(target, _dump_yaml(merged))

        validate_root = args.validate_root.resolve() if args.validate_root else target.parent
        ok, validation_output = _run_validator(validate_root)
        report["validation"] = {"status": "PASSED" if ok else "FAILED", "root": str(validate_root), "output": validation_output}
        if not ok:
            if original_exists and original_bytes is not None:
                target.write_bytes(original_bytes)
            elif target.exists():
                target.unlink()
            report["status"] = "ROLLED_BACK"
            _write_report(args.report.resolve() if args.report else None, report)
            return 4

        report["status"] = "PASSED"
        report["output"] = str(target)
        _write_report(args.report.resolve() if args.report else None, report)
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
