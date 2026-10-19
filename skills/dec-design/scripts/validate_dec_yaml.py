#!/usr/bin/env python3
"""DEC validation CLI. Semantic passes are independently testable modules."""
from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path
from typing import Mapping
import yaml
from yaml_to_xml import ConversionError, convert_document, infer_kind, load_yaml
from validation_common import ValidationContext, DESIGN_ID_RE, err, walk, yaml_files
from validation_local import LOCAL_VALIDATORS
from validation_references import validate_datasources, validate_data_ownership, validate_enum_references, validate_views, validate_api_references, validate_rule_references
from validation_information import validate_information, validate_information_changes, validate_model_access
from validation_business import validate_business_references


def validate(source: Path) -> dict:
    source = source.resolve()
    files = yaml_files(source)
    ctx = ValidationContext(source)
    for file in files:
        try:
            root = load_yaml(file)
            kind = infer_kind(root)
            convert_document(root)  # unknown-key/type-shape validation
            ctx.docs.append((file, root, kind))
        except (ConversionError, yaml.YAMLError) as exc:
            err(ctx.errors, file, str(exc))
            continue
        for node_path, node in walk(root):
            ident = node.get("id") if isinstance(node, Mapping) else None
            if ident and isinstance(ident, str) and DESIGN_ID_RE.match(ident):
                prior = ctx.ids.get(ident)
                here = f"{file}:{node_path}"
                if prior:
                    err(ctx.errors, file, f"duplicate id {ident}: {prior} and {here}")
                else:
                    ctx.ids[ident] = here


    if not files:
        err(ctx.errors, source, "no canonical YAML files found")
    for file, root, kind in ctx.docs:
        LOCAL_VALIDATORS[kind](file, root, ctx)
    validate_datasources(ctx)
    validate_data_ownership(ctx)
    validate_enum_references(ctx)
    validate_views(ctx)
    validate_api_references(ctx)
    validate_rule_references(ctx)
    validate_information(ctx)
    validate_information_changes(ctx)
    validate_model_access(ctx)
    validate_business_references(ctx)
    result = {
        "status": "PASSED" if not ctx.errors else "FAILED",
        "files": len(files),
        "stableIds": len(ctx.ids),
        "datas": len(ctx.data_defs),
        "views": len(ctx.view_defs),
        "ruleViews": len(ctx.rule_view_defs),
        "apis": len(ctx.api_defs),
        "enums": len(ctx.enum_defs),
        "systems": len(ctx.system_defs),
        "informationKeys": len(ctx.information_defs),
        "dataSources": len(ctx.datasource_names),
        "connections": len(ctx.connection_names),
        "errors": ctx.errors,
        "warnings": ctx.warnings,
    }
    return result


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Validate DEC canonical YAML structure, semantics and cross-file references")
    parser.add_argument("input", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    result = validate(args.input)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"{result['status']}: files={result['files']} ids={result['stableIds']} "
              f"datas={result['datas']} views={result['views']} ruleViews={result['ruleViews']} "
              f"apis={result['apis']} enums={result['enums']} systems={result['systems']} "
              f"informationKeys={result['informationKeys']}")
        for item in result['warnings']:
            print(f"WARN {item['file']}: {item['message']}")
        for item in result['errors']:
            print(f"ERROR {item['file']}: {item['message']}", file=sys.stderr)
    return 0 if not result['errors'] else 1


if __name__ == "__main__":
    raise SystemExit(main())
