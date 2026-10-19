"""Focused cross-file validation passes over a shared ValidationContext."""
from __future__ import annotations
from typing import Dict, Mapping, Set
from dec_common import extract_info_refs, parse_change_assignments
from validation_common import ValidationContext, as_list, err, warn, extract_property_refs, resolve_view_property_enum, cycle_nodes

def validate_information(ctx: ValidationContext) -> None:
    # System references + Information graph.
    graph: Dict[str, Set[str]] = {}
    for sname, (file, system) in ctx.system_defs.items():
        for ref in as_list(system.get("dataRefs")):
            name = ref.get("name") if isinstance(ref, Mapping) else ref
            if ctx.data_defs and name and str(name) not in ctx.data_defs:
                err(ctx.errors, file, f"System {sname}.dataRefs: Data {name!r} not found")
        for ref in as_list(system.get("viewRefs")):
            name = ref.get("name") if isinstance(ref, Mapping) else ref
            if ctx.view_defs and name and str(name) not in ctx.view_defs:
                err(ctx.errors, file, f"System {sname}.viewRefs: View {name!r} not found")

    for ikey, (file, info, owner) in ctx.information_defs.items():
        if info.get("expression"):
            refs = extract_info_refs(str(info.get("expression")))
            graph[ikey] = refs
            for ref in refs:
                if ref not in ctx.information_defs:
                    err(ctx.errors, file, f"Information {ikey}.expression: referenced Information {ref!r} not found")
        else:
            graph[ikey] = set()
            vref = str(info.get("viewRef")) if info.get("viewRef") else ""
            system = ctx.system_defs.get(owner, (None, {}))[1]
            declared_views = {str(x.get("name") if isinstance(x, Mapping) else x) for x in as_list(system.get("viewRefs"))}
            if declared_views and vref not in declared_views:
                err(ctx.errors, file, f"Information {ikey}.viewRef: {vref!r} is not owned/declared by System {owner}")
            rref = info.get("ruleRef")
            if rref:
                if ctx.rule_view_defs and str(rref) not in ctx.rule_view_defs:
                    err(ctx.errors, file, f"Information {ikey}.ruleRef: RuleView {rref!r} not found")
                elif str(rref) in ctx.rule_view_defs:
                    rv_view = str(ctx.rule_view_defs[str(rref)][1].get("viewRef"))
                    if vref and rv_view != vref:
                        err(ctx.errors, file, f"Information {ikey}: viewRef {vref!r} incompatible with RuleView {rref!r}.viewRef {rv_view!r}")
                elif not ctx.rule_view_defs:
                    warn(ctx.warnings, file, f"Information {ikey}.ruleRef cross-file validation skipped because no rule YAML was loaded")
    cyc = cycle_nodes(graph)
    if cyc:
        err(ctx.errors, ctx.source, "Information dependency cycle: " + ", ".join(sorted(cyc)))


def validate_information_changes(ctx: ValidationContext) -> None:
    # Information changeData ↔ Enum consistency. When a changed View property
    # ultimately maps to a Data column with relEnum, the assigned value must be
    # one of that Enum's concrete values. This keeps state materialization and
    # the shared Enum fact aligned.
    for ikey, (file, info, owner) in ctx.information_defs.items():
        change_data = info.get("changeData")
        view_ref = str(info.get("viewRef") or "")
        if not change_data or not view_ref or view_ref not in ctx.view_defs:
            continue
        view = ctx.view_defs[view_ref][1]
        for prop_path, changed_value in parse_change_assignments(str(change_data)):
            enum_refs = resolve_view_property_enum(view, prop_path, ctx.data_defs)
            if len(enum_refs) > 1:
                err(ctx.errors, file, f"Information {ikey}.changeData {prop_path}: maps to multiple Enums {sorted(enum_refs)}")
                continue
            if not enum_refs:
                continue
            enum_ref = next(iter(enum_refs))
            if ctx.enum_defs and enum_ref not in ctx.enum_defs:
                err(ctx.errors, file, f"Information {ikey}.changeData {prop_path}: Enum {enum_ref!r} not found")
                continue
            if enum_ref in ctx.enum_defs:
                values = [v.get("value") for v in as_list(ctx.enum_defs[enum_ref][1].get("values")) if isinstance(v, Mapping)]
                if not any(str(v) == str(changed_value) for v in values):
                    err(ctx.errors, file, f"Information {ikey}.changeData {prop_path}: value {changed_value!r} is not defined by Enum {enum_ref}")
            elif not ctx.enum_defs:
                warn(ctx.warnings, file, f"Information {ikey}.changeData {prop_path}: Enum {enum_ref!r} validation skipped because no enum YAML was loaded")


def validate_model_access(ctx: ValidationContext) -> None:
    # ModelAccess.
    for sname, (file, system) in ctx.system_defs.items():
        for mi, ma in enumerate(as_list(system.get("modelAccess"))):
            if not isinstance(ma, Mapping):
                continue
            model_ref = ma.get("modelRef")
            if not model_ref:
                err(ctx.errors, file, f"System {sname}.modelAccess[{mi}].modelRef: required")
                continue
            model_ref = str(model_ref)
            if ctx.view_defs and model_ref not in ctx.view_defs:
                err(ctx.errors, file, f"System {sname}.modelAccess[{mi}].modelRef: View {model_ref!r} not found")
            for mode in ("read", "write"):
                for ai, access in enumerate(as_list(ma.get(mode))):
                    if not isinstance(access, Mapping):
                        err(ctx.errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}]: expected mapping")
                        continue
                    if not access.get("path"):
                        err(ctx.errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}].path: required")
                    refs = access.get("refs") if access.get("refs") is not None else access.get("ref")
                    ref_items = as_list(refs)
                    if not ref_items:
                        err(ctx.errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}].ref: at least one required")
                    for ri, ref in enumerate(ref_items):
                        if not isinstance(ref, Mapping):
                            err(ctx.errors, file, f"System {sname}.modelAccess[{mi}].{mode}[{ai}].ref[{ri}]: expected mapping")
                            continue
                        v = ref.get("view")
                        prop = ref.get("property")
                        if not v: err(ctx.errors, file, f"...ref[{ri}].view: required")
                        if not prop: err(ctx.errors, file, f"...ref[{ri}].property: required")
                        if ctx.view_defs and v and str(v) not in ctx.view_defs:
                            err(ctx.errors, file, f"System {sname}.modelAccess ref view {v!r} not found")
                        if ctx.view_defs and v and str(v) in ctx.view_defs and prop:
                            view = ctx.view_defs[str(v)][1]
                            valid_props = extract_property_refs(view.get("properties") or {})
                            target_main = str(view.get("targetMain", ""))
                            # Current exact binding allows target-main name first, then property path.
                            if str(prop) != target_main and str(prop) not in valid_props:
                                err(ctx.errors, file, f"System {sname}.modelAccess ref property {prop!r} not found in View {v} or targetMain {target_main!r}")


