import subprocess
import os
import sys
import tempfile
import unittest
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


class RenderDecGraphTest(unittest.TestCase):
    def test_information_pages_are_stable_across_hash_seeds(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            pages = []
            for seed in ("11", "97"):
                out = base / seed / "order-payment.html"
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
            out = base / "order-payment.html"
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
            self.assertIn('data-source="dir:paying" data-target="dep:paying:0" data-kind="dependency"', detail)
            self.assertNotIn('data-source="dep:paying:0" data-target="dir:paying"', detail)
            self.assertNotIn('dep:success:', detail)
            self.assertNotIn('dep:error:', detail)


    def test_reserved_system_name_directory_does_not_collide_with_directory_maps(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "order-payment.html"
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
            systems = (base / "order-payment-systems.html").read_text(encoding="utf-8")
            self.assertIn("system/directory/index.html", systems)

    def test_regeneration_removes_only_legacy_generated_system_directory(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "order-payment.html"
            legacy = base / "user"; legacy.mkdir()
            legacy_html = legacy / "index.html"
            legacy_html.write_text("<meta name='generator' content='dec-design/scripts/render_dec_graph.py'>", encoding="utf-8")
            self.run_render(out, ROOT / "examples")
            self.assertFalse(legacy.exists())
            self.assertTrue((base / "system" / "user" / "index.html").exists())

    def test_system_page_navigates_data_view_rule_enum_api_information(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); out = base / "order-payment.html"
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
            base = Path(td); out = base / "order-payment.html"
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
            base = Path(td); out = base / "order-payment.html"
            self.run_render(out, ROOT / "examples")
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
            base = Path(td); out = base / "order-payment.html"
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
