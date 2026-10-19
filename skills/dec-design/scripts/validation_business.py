"""Focused cross-file validation passes over a shared ValidationContext."""
from __future__ import annotations
from collections import defaultdict
from typing import Dict, List, Mapping, Tuple
from validation_common import ValidationContext, as_list, err, warn

def validate_business_references(ctx: ValidationContext) -> None:
    # Business and Action logical DataSource references (including Back).
    def check_business_source(node, file, location):
        if "dataSource" not in node:
            return
        source = node["dataSource"]
        if not isinstance(source, str) or not source.strip():
            err(ctx.errors, file, f"{location}.dataSource: non-empty string required")
        elif ctx.datasource_names and source not in ctx.datasource_names:
            err(ctx.errors, file, f"{location}.dataSource: DataSource {source!r} not found")
        elif not ctx.datasource_names:
            if any(kind == "config" for _, _, kind in ctx.docs):
                err(ctx.errors, file, f"{location}.dataSource: DataSource {source!r} not found")
            else:
                warn(ctx.warnings, file, f"{location}.dataSource cross-file check skipped because no config YAML was loaded")

    # Business refs.
    for file, root, kind in ctx.docs:
        if kind != "business":
            continue
        business = root.get("business") or {}
        check_business_source(business, file, "business")
        for directory in as_list(business.get("directories")):
            if not isinstance(directory, Mapping): continue
            actions = list(as_list(directory.get("actions")))
            for sub in as_list(directory.get("subDirectories")):
                if isinstance(sub, Mapping) and isinstance(sub.get("back"), Mapping):
                    actions.extend(as_list(sub["back"].get("actions")))
            for action in actions:
                if isinstance(action, Mapping):
                    check_business_source(action, file, f"Action {action.get('name')}")
        dirs = as_list(business.get("directories"))
        names = {str(d.get("name")) for d in dirs if isinstance(d, Mapping) and d.get("name")}
        case_targets: Dict[str, List[Tuple[str, str]]] = defaultdict(list)
        for parent in dirs:
            if not isinstance(parent, Mapping) or not parent.get("name"):
                continue
            for sub in as_list(parent.get("subDirectories")):
                if isinstance(sub, Mapping) and sub.get("role") == "case" and sub.get("rel"):
                    case_targets[str(sub.get("rel"))].append((str(parent.get("name")), str(sub.get("informationRef") or "")))
        for di, directory in enumerate(dirs):
            if not isinstance(directory, Mapping) or not directory.get("name"):
                continue
            dname = str(directory["name"])
            if dname in case_targets and as_list(directory.get("dependencies")):
                parents = ", ".join(p for p, _ in case_targets[dname])
                err(ctx.errors, file, f"Directory {dname}.dependencies: case Directory target already receives its gate from {parents}; do not duplicate depend conditions")
            model_ref = directory.get("modelRef")
            if ctx.view_defs and model_ref and str(model_ref) not in ctx.view_defs:
                err(ctx.errors, file, f"Directory {dname}.modelRef: View {model_ref!r} not found")
            elif not ctx.view_defs and model_ref:
                warn(ctx.warnings, file, f"Directory {dname}.modelRef cross-file validation skipped because no view YAML was loaded")

            info_refs: List[Tuple[str, str]] = []
            if directory.get("informationRef"): info_refs.append(("informationRef", str(directory["informationRef"])))
            change = directory.get("change")
            if isinstance(change, Mapping) and change.get("informationRef"):
                info_refs.append(("change.informationRef", str(change["informationRef"])))
            for depi, dep in enumerate(as_list(directory.get("dependencies"))):
                if isinstance(dep, Mapping) and dep.get("informationRef"):
                    info_refs.append((f"dependencies[{depi}]", str(dep["informationRef"])))
            for subi, sub in enumerate(as_list(directory.get("subDirectories"))):
                if not isinstance(sub, Mapping):
                    continue
                rel = sub.get("rel")
                if rel and str(rel) not in names:
                    err(ctx.errors, file, f"Directory {dname}.subDirectories[{subi}].rel: Directory {rel!r} not found")
                if rel and str(rel) == dname:
                    err(ctx.errors, file, f"Directory {dname}.subDirectories[{subi}]: self relation is not allowed")
                if sub.get("informationRef"):
                    info_refs.append((f"subDirectories[{subi}].informationRef", str(sub["informationRef"])))
                back = sub.get("back")
                if isinstance(back, Mapping):
                    if not back.get("name"): err(ctx.errors, file, f"Directory {dname}.subDirectories[{subi}].back.name: required")
                    for bai, action in enumerate(as_list(back.get("actions"))):
                        if isinstance(action, Mapping) and action.get("ruleRef") and not action.get("systemRef"):
                            err(ctx.errors, file, f"Directory {dname}.back.actions[{bai}].systemRef: required when ruleRef is used")
            for ai, action in enumerate(as_list(directory.get("actions"))):
                if not isinstance(action, Mapping):
                    continue
                sref = action.get("systemRef")
                rref = action.get("ruleRef")
                if rref:
                    if ctx.system_defs and str(sref) not in ctx.system_defs:
                        err(ctx.errors, file, f"Directory {dname}.actions[{ai}].systemRef: System {sref!r} not found")
                    if ctx.rule_view_defs and str(rref) not in ctx.rule_view_defs:
                        err(ctx.errors, file, f"Directory {dname}.actions[{ai}].ruleRef: RuleView {rref!r} not found")
                    if str(rref) in ctx.rule_view_defs and model_ref:
                        rv_view = str(ctx.rule_view_defs[str(rref)][1].get("viewRef"))
                        if rv_view != str(model_ref):
                            err(ctx.errors, file, f"Directory {dname}.actions[{ai}]: RuleView {rref}.viewRef {rv_view!r} incompatible with directory modelRef {model_ref!r}")
                for pi, prod in enumerate(as_list(action.get("produces"))):
                    if isinstance(prod, Mapping) and prod.get("informationRef"):
                        ref = str(prod["informationRef"])
                        info_refs.append((f"actions[{ai}].produces[{pi}].informationRef", ref))
                        if ref in ctx.information_defs and ctx.information_defs[ref][1].get("expression"):
                            err(ctx.errors, file, f"Directory {dname}.actions[{ai}].produces[{pi}]: informationRef {ref!r} is composite; Produce should map to an atomic Information")

            if ctx.information_defs:
                for loc, ref in info_refs:
                    if ref not in ctx.information_defs:
                        err(ctx.errors, file, f"Directory {dname}.{loc}: Information {ref!r} not found")
                if isinstance(change, Mapping) and change.get("informationRef"):
                    cref = str(change["informationRef"])
                    if cref in ctx.information_defs and not ctx.information_defs[cref][1].get("changeData"):
                        warn(ctx.warnings, file, f"Directory {dname}.change: Information {cref!r} has no changeData; ensure an Action materializes it")
            elif info_refs:
                warn(ctx.warnings, file, f"Directory {dname}: Information cross-file validation skipped because no systems YAML was loaded")


