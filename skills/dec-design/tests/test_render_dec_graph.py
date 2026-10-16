import subprocess
import os
import sys
import tempfile
import unittest
import re
import shutil
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import render_dec_graph as renderer
sys.path.pop(0)


class RenderDecGraphTest(unittest.TestCase):
    def test_information_nodes_embed_complete_rules_and_collapsed_controls(self):
        docs = renderer.load_docs(renderer.discover([str(ROOT.parents[1] / "demo" / "order-payment-yaml")]))
        catalog = renderer.build_catalog(docs)
        infos = renderer.system_information_context(catalog["infos"], "order")
        markup, templates = renderer.article_information_graph(infos, "order", renderer.page_names(Path("directories.html"), catalog["systems"]), catalog)
        svg = ET.fromstring(markup)
        nodes = {g.get("data-node-id"): g for g in svg.iter("g") if g.get("data-node-id")}
        for key in infos:
            origin = nodes[f"info:{key}"]
            controls = origin.findall("g")
            self.assertEqual({"up", "down", "left", "right"}, {c.get("data-direction") for c in controls})
            self.assertIsNone(origin.find("foreignObject"))
            for control in controls:
                self.assertEqual("false", control.get("aria-expanded"))
                self.assertEqual("button", control.get("role"))
                panel = nodes[control.get("data-panel-id")]
                self.assertEqual("display:none", panel.get("style"))
                self.assertEqual("rule-face", panel.find("rect").get("class"))
                edge = next(g for g in svg.iter("g") if g.get("data-target") == control.get("data-panel-id"))
                self.assertEqual(f"info:{key}", edge.get("data-source"))
                self.assertEqual("rule", edge.get("data-kind"))
                self.assertIn(f'data-detail-id="{control.get("data-panel-id")}"', templates)
        wait = "".join(nodes["rule:order.waitPay:right"].find("foreignObject").itertext())
        for expected in ("isWaitPay", "RV-ORDER-IS-WAIT-PAY", "RULE-ORDER-IS-WAIT-PAY", "checkPattern", "status = 1", "data1"):
            self.assertIn(expected, wait)
        ordered = "".join(nodes["rule:order.ordered:right"].find("foreignObject").itertext())
        self.assertIn(infos["order.ordered"]["ruleData"], ordered)
        self.assertIn(infos["order.ordered"]["changeData"], ordered)
        payable = "".join(nodes["rule:order.payable:right"].find("foreignObject").itertext())
        self.assertIn(infos["order.payable"]["expression"], payable)
        self.assertIn("未包含", renderer.information_inline_html({"ruleRef": "missing"}, catalog))
        self.assertNotIn("<script>", renderer.information_inline_html({"ruleData": "<script>alert(1)</script>"}, catalog))

    def test_ordered_and_wait_pay_share_rule_sections_and_sidebar_content(self):
        docs = renderer.load_docs(renderer.discover([str(ROOT.parents[1] / "demo" / "order-payment-yaml")]))
        catalog = renderer.build_catalog(docs)
        infos = renderer.system_information_context(catalog["infos"], "order")
        pages = renderer.page_names(Path("directories.html"), catalog["systems"])
        _, templates = renderer.article_information_graph(infos, "order", pages, catalog)
        details = dict(re.findall(r'<template class="node-template" data-detail-id="([^"]+)">(.*?)</template>', templates, re.S))
        for key in ("order.ordered", "order.waitPay"):
            info = infos[key]
            rules = renderer.information_rules_html(info, catalog)
            self.assertIn("<h4>识别规则</h4>", rules)
            self.assertIn(rules, renderer.information_inline_html(info, catalog))
            self.assertIn(rules, details[f"info:{key}"])
            for direction in ("up", "down", "left", "right"):
                self.assertEqual(details[f"info:{key}"], details[f"rule:{key}:{direction}"])
            self.assertEqual(1, details[f"info:{key}"].count("<h4>识别规则</h4>"))
        ordered = details["info:order.ordered"]
        self.assertIn("every(orderDetailList, status = 1)", ordered)
        self.assertIn("every(orderDetailList, status : 1)", ordered)
        self.assertNotIn("isWaitPay", ordered)
        self.assertIn("RULE-ORDER-IS-WAIT-PAY", details["info:order.waitPay"])

    def test_information_expand_collapse_event_handlers(self):
        node = shutil.which("node")
        if not node:
            self.skipTest("Node.js is required to execute the generated interaction handlers")
        with tempfile.TemporaryDirectory() as td:
            script = Path(td) / "interaction.js"
            script.write_text(renderer.JS, encoding="utf-8")
            result = subprocess.run([node, str(ROOT / "tests" / "check_information_interaction.cjs"), str(script)], text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_business_dependency_expands_information_then_rules(self):
        docs = renderer.load_docs(renderer.discover([str(ROOT.parents[1] / "demo" / "order-payment-yaml")]))
        cat = renderer.build_catalog(docs)
        pages = renderer.page_names(Path("directories.html"), cat["systems"])
        markup, templates = renderer.business_information_expansion("dep:pay:0", "order.payable", '<rect class="info-face" x="0" y="0" width="155" height="48"/>', (0,0,155,48), cat, pages)
        svg = ET.fromstring(f'<svg>{markup}</svg>')
        nodes = {n.get("data-node-id"): n for n in svg.iter("g") if n.get("data-node-id")}
        self.assertEqual(5, len(nodes))  # payable, ordered, waitPay, and two rule frames
        root = nodes["dep:pay:0"]
        self.assertEqual("true", root.get("data-expansion-root"))
        self.assertEqual(4, len(root.findall("g")))
        edges = [n for n in svg.iter("g") if n.get("data-expansion-edge")]
        self.assertEqual(4, len(edges))
        for edge in edges:
            self.assertIn(edge.get("data-source"), nodes)
            self.assertIn(edge.get("data-target"), nodes)
            self.assertEqual("display:none", edge.get("style"))
            if edge.get("data-source") == "dep:pay:0":
                self.assertEqual("OR · 任一", edge.find("text").text)
        for key, node in nodes.items():
            self.assertIn(f'data-detail-id="{key}"', templates)
            if key != "dep:pay:0":
                self.assertEqual("display:none", node.get("style"))
        self.assertIn("every(orderDetailList, status = 1)", markup)
        self.assertIn("RULE-ORDER-IS-WAIT-PAY", markup)
        business = next(b for b in cat["businesses"] if b["name"] == "order-payment")
        rendered, _ = renderer.article_business_graph(business, cat, pages)
        self.assertIn('data-expansion-root="true"', rendered)
        self.assertIn("OR · 任一", rendered)

    def test_information_tree_groups_related_facts_and_exposes_or_semantics(self):
        docs = renderer.load_docs(renderer.discover([str(ROOT.parents[1] / "demo" / "order-payment-yaml")]))
        catalog = renderer.build_catalog(docs)
        infos = renderer.system_information_context(catalog["infos"], "order")
        positions = renderer.information_layout(infos)
        root = positions["order.payable"]
        children = [positions[name] for name in ("order.ordered", "order.waitPay")]
        self.assertTrue(all(root[0] < child[0] for child in children))
        self.assertEqual(sum(child[1] for child in children) / 2, root[1])
        for name in ("order.payErrorStatus", "order.paySuccessStatus", "order.paying"):
            self.assertGreater(positions[name][1], max(child[1] for child in children) + 136)
        pages = renderer.page_names(Path("directories.html"), catalog["systems"])
        markup, _ = renderer.article_information_graph(infos, "order", pages, catalog)
        svg = ET.fromstring(markup)
        edges = [g for g in svg.iter("g") if g.get("data-kind") == "depends"]
        self.assertEqual(2, len(edges))
        for edge in edges:
            self.assertEqual("info:order.payable", edge.get("data-source"))
            self.assertEqual("OR · 任一", edge.find("text").text)
        rectangles = []
        for group in svg.iter("g"):
            if not group.get("data-node-id") or group.get("style") == "display:none":
                continue
            box = next(rect for rect in group.findall("rect") if rect.get("class") != "info-node-back")
            rectangles.append(tuple(float(box.get(k)) for k in ("x", "y", "width", "height")))
        for i, (x, y, width, height) in enumerate(rectangles):
            for ox, oy, ow, oh in rectangles[i+1:]:
                self.assertTrue(x+width <= ox or ox+ow <= x or y+height <= oy or oy+oh <= y)
        page = renderer.render_information_html("order", catalog, pages, docs)
        self.assertIn("组合条件（canonical expression）", page)
        self.assertIn("<aside class='inspector'><h3>Information detail</h3>", page)
        self.assertNotIn(".information-layout{display:block}", page)
        self.assertIn(renderer.h(infos["order.payable"]["expression"]), page)

    def test_information_labels_preserve_negation_and_do_not_flatten_mixed_logic(self):
        label = renderer.information_relation_label
        self.assertEqual("AND · 全部", label({"expression": "user.active and user.certified"}))
        self.assertEqual("AND · NOT", label({"expression": "order.ordered and not\tpayment.hasResult"}, "payment.hasResult"))
        self.assertEqual("NOT · 取反", label({"expression": "not payment.hasResult"}, "payment.hasResult"))
        for expression in ("user.active and (user.certified or order.ordered)",
                           "user.active or user.certified and order.ordered",
                           "user.active or not user.active"):
            self.assertEqual("表达式引用", label({"expression": expression}))

    def test_information_layout_keeps_shared_dependencies_unique_and_rejects_cycles(self):
        infos = {"x.first": {"dependsOn": ["x.shared"]},
                 "x.second": {"dependsOn": ["x.shared"]},
                 "x.shared": {"dependsOn": []}, "x.isolated": {"dependsOn": []}}
        positions = renderer.information_layout(infos)
        self.assertEqual(set(infos), set(positions))
        self.assertEqual(positions, renderer.information_layout(dict(reversed(list(infos.items())))))
        for key in ("x.first", "x.second"):
            self.assertLess(positions[key][0], positions["x.shared"][0])
        with self.assertRaisesRegex(ValueError, "dependency cycle"):
            renderer.information_layout({"x.a": {"dependsOn": ["x.b"]}, "x.b": {"dependsOn": ["x.a"]}})

    def test_information_descriptions_wrap_by_display_width(self):
        text = "订单满足可支付条件，依赖 ordered 或 waitPay，并保留完整描述。"
        lines = renderer.information_text_lines(text, 42, 3)
        self.assertGreater(len(lines), 1)
        self.assertEqual(text, "".join(lines))
        for line in lines:
            self.assertLessEqual(sum(2 if renderer.unicodedata.east_asian_width(c) in "WF" else 1 for c in line), 42)
        shortened = renderer.information_text_lines(text * 5, 42, 3)
        self.assertEqual(3, len(shortened))
        self.assertTrue(shortened[-1].endswith("…"))

    def test_change_edges_have_visible_interactive_targets_and_details(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            self.run_render(base, ROOT.parents[1] / "demo" / "order-payment-yaml")
            detail = (base / "directory" / "order-payment.html").read_text(encoding="utf-8")
            svg = ET.fromstring(re.search(r'<svg class="interactive-svg".*?</svg>', detail, re.S).group())
            nodes = {node.get("data-node-id"): node for node in svg.iter("g") if node.get("data-node-id")}
            changes = [edge for edge in svg.iter("g") if edge.get("data-kind") == "change"]
            self.assertEqual(3, len(changes))
            for edge in svg.iter("g"):
                if edge.get("data-kind"):
                    self.assertIn(edge.get("data-source"), nodes)
                    self.assertIn(edge.get("data-target"), nodes)
            for edge in changes:
                target = nodes[edge.get("data-target")]
                self.assertIsNotNone(target.find("polygon"))
                self.assertTrue("".join(target.itertext()).strip())
                self.assertIn(f'data-detail-id="{edge.get("data-target")}"', detail)
            for ref in ("common.paying", "common.paySuccess", "common.payError"):
                self.assertIn(ref, detail)
            self.assertIn("<h4>Change data</h4>", detail)

    def test_legacy_file_argument_migrates_to_neutral_names_and_preserves_user_files(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            marker = "<meta name='generator' content='dec-design/scripts/render_dec_graph.py'>"
            old = base / "order-payment.html"
            old.write_text(marker, encoding="utf-8")
            (base / "order-payment-index.html").write_text(marker, encoding="utf-8")
            manual = base / "order-payment-data.html"
            manual.write_text("user-maintained notes", encoding="utf-8")
            self.run_render(old, ROOT / "examples")
            self.assertFalse(old.exists())
            self.assertFalse((base / "order-payment-index.html").exists())
            self.assertEqual("user-maintained notes", manual.read_text(encoding="utf-8"))
            for name in ("index", "directories", "datasources", "data", "views", "ruleviews", "apis", "systems", "enums"):
                self.assertTrue((base / f"{name}.html").is_file())

    def test_business_filename_collision_fails_before_overwrite(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        try:
            from render_dec_graph import write_site
            with tempfile.TemporaryDirectory() as td:
                base = Path(td)
                sentinel = base / "index.html"
                sentinel.write_text("keep", encoding="utf-8")
                docs = [(Path(f"business-{i}.yaml"), {"kind": "business", "business": {"name": name}})
                        for i, name in enumerate(("Review Flow", "review-flow"))]
                with self.assertRaisesRegex(ValueError, "filename collision"):
                    write_site(base, docs)
                self.assertEqual("keep", sentinel.read_text(encoding="utf-8"))
        finally:
            sys.path.pop(0)

    def test_information_pages_are_stable_across_hash_seeds(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            pages = []
            for seed in ("11", "97"):
                out = base / seed / "directories.html"
                proc = subprocess.run(
                    [sys.executable, str(ROOT / "scripts" / "render_dec_graph.py"),
                     str(ROOT / "examples"), "-o", str(out)],
                    env={**os.environ, "PYTHONHASHSEED": seed}, text=True, capture_output=True,
                )
                self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
                pages.append((out.parent / "system" / "order" / "information.html").read_bytes())
            self.assertEqual(pages[0], pages[1])

    def run_render(self, out: Path, *inputs: Path):
        proc = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "render_dec_graph.py"), *(str(x) for x in inputs), "-o", str(out)],
            text=True, capture_output=True,
        )
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        return proc

    def test_example_generates_system_directories_and_directory_map_index(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            out = base / "directories.html"
            self.run_render(out, ROOT / "examples")
            for system in ("user", "order", "payment", "common"):
                folder = base / "system" / system
                self.assertTrue(folder.is_dir())
                for name in ("index.html", "data.html", "views.html", "ruleviews.html", "apis.html", "enums.html", "information.html"):
                    self.assertTrue((folder / name).exists(), f"{system}/{name}")
            self.assertTrue((base / "directory" / "order-payment.html").exists())
            for system in ("user", "order", "payment", "common"):
                self.assertFalse((base / system).exists(), f"legacy root-level System directory must not exist: {system}")
            directory_index = out.read_text(encoding="utf-8")
            self.assertIn("Business Directory Maps", directory_index)
            self.assertIn("directory/order-payment.html", directory_index)
            detail = (base / "directory" / "order-payment.html").read_text(encoding="utf-8")
            self.assertIn("Business Directory Map · order-payment", detail)
            self.assertIn("href='../directories.html'>Directory</a>", detail)
            self.assertIn('data-source="dir:paying" data-target="dep:paying:0" data-kind="dependency"', detail)
            self.assertNotIn('data-source="dep:paying:0" data-target="dir:paying"', detail)
            self.assertNotIn('dep:success:', detail)
            self.assertNotIn('dep:error:', detail)


    def test_reserved_system_name_directory_does_not_collide_with_directory_maps(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "directories.html"
            extra = base / "systems-directory.yaml"
            extra.write_text("""kind: systems
version: dec/v1
systems:
  - id: SYS-DIRECTORY
    name: directory
    desc: Reserved-name collision regression System
    information: []
""", encoding="utf-8")
            self.run_render(out, ROOT / "examples", extra)
            self.assertTrue((base / "system" / "directory" / "index.html").exists())
            self.assertTrue((base / "directory" / "order-payment.html").exists())
            systems = (base / "systems.html").read_text(encoding="utf-8")
            self.assertIn("system/directory/index.html", systems)

    def test_regeneration_removes_only_legacy_generated_system_directory(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "directories.html"
            legacy = base / "user"; legacy.mkdir()
            legacy_html = legacy / "index.html"
            legacy_html.write_text("<meta name='generator' content='dec-design/scripts/render_dec_graph.py'>", encoding="utf-8")
            self.run_render(out, ROOT / "examples")
            self.assertFalse(legacy.exists())
            self.assertTrue((base / "system" / "user" / "index.html").exists())

    def test_system_page_navigates_data_view_rule_enum_api_information(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "directories.html"
            self.run_render(out, ROOT / "examples")
            order = (base / "system" / "order" / "index.html").read_text(encoding="utf-8")
            self.assertIn("<h2>Data</h2>", order)
            self.assertIn("data.html#data-order", order)
            self.assertIn("views.html#view-orderinfo", order)
            self.assertIn("ruleviews.html#ruleview-save-order", order)
            self.assertIn("enums.html#enum-orderstatus", order)
            self.assertIn("apis.html#api-order.submitorder", order)
            self.assertIn("information.html#info-order.payable", order)
            common = (base / "system" / "common" / "index.html").read_text(encoding="utf-8")
            self.assertIn("DEC System · common", common)

    def test_view_page_has_hierarchy_type_desc_and_enum_from_data(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "directories.html"
            self.run_render(out, ROOT / "examples")
            view = (base / "system" / "order" / "views.html").read_text(encoding="utf-8")
            self.assertIn("Property / child object", view)
            self.assertIn("Description", view)
            self.assertIn("订单所属用户", view)
            self.assertIn("orderDetailList", view)
            self.assertIn("one-to-many", view)
            self.assertIn("<strong>int</strong>", view)
            self.assertIn("enums.html#enum-orderstatus", view)
            self.assertIn("Type 来自其引用的 Data property", view)


    def test_generated_site_has_no_broken_local_links(self):
        class Parser(HTMLParser):
            def __init__(self):
                super().__init__(); self.hrefs = []; self.ids = set()
            def handle_starttag(self, tag, attrs):
                values = dict(attrs)
                if values.get("id"): self.ids.add(values["id"])
                if tag == "a" and values.get("href"): self.hrefs.append(values["href"])

        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "directories.html"
            self.run_render(out, ROOT / "examples", ROOT.parents[1] / "dec-java-lite" / "fixtures" / "expense-approval")
            listing = out.read_text(encoding="utf-8")
            self.assertIn("directory/order-payment.html", listing)
            self.assertIn("directory/expense-approval.html", listing)
            self.assertFalse(list(base.glob("order-payment-*.html")))
            parsed = {}
            for file in base.rglob("*.html"):
                parser = Parser(); parser.feed(file.read_text(encoding="utf-8")); parsed[file.resolve()] = parser
            errors = []
            for file, parser in parsed.items():
                for raw in parser.hrefs:
                    if raw.startswith(("http:", "https:", "mailto:", "javascript:")):
                        continue
                    split = urlsplit(raw); target = ((file.parent / unquote(split.path)).resolve() if split.path else file)
                    if target not in parsed:
                        errors.append(f"{file.relative_to(base)} -> {raw}: missing file")
                    elif split.fragment and unquote(split.fragment) not in parsed[target].ids:
                        errors.append(f"{file.relative_to(base)} -> {raw}: missing anchor")
            self.assertEqual([], errors)

    def test_information_dependency_direction_and_overwrite(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "directories.html"
            out.write_text("SENTINEL MANUAL EDIT", encoding="utf-8")
            self.run_render(out, ROOT / "examples")
            self.assertNotIn("SENTINEL MANUAL EDIT", out.read_text(encoding="utf-8"))
            user = (base / "system" / "user" / "information.html").read_text(encoding="utf-8")
            self.assertIn('data-source="info:user.effective" data-target="info:user.activated"', user)
            self.assertIn('data-source="info:user.effective" data-target="info:user.certified"', user)
            self.assertIn("views.html#view-userinfo", user)
            self.assertIn("ruleviews.html#ruleview-isactivated", user)


if __name__ == "__main__":
    unittest.main()
