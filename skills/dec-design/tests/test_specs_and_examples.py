import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]

KIND_TO_SCHEMA = {
    "config": "config.schema.json",
    "data": "data.schema.json",
    "view": "view.schema.json",
    "rule": "rule.schema.json",
    "api": "api.schema.json",
    "enum": "enum.schema.json",
    "systems": "systems.schema.json",
    "business": "business.schema.json",
}


class SpecAndExampleTest(unittest.TestCase):
    def test_all_schemas_are_valid_draft_2020_12(self):
        for path in sorted((ROOT / "schemas").glob("*.schema.json")):
            schema = json.loads(path.read_text(encoding="utf-8"))
            Draft202012Validator.check_schema(schema)

    def test_all_examples_match_their_json_schema(self):
        for path in sorted((ROOT / "examples").glob("*.yaml")):
            data = yaml.safe_load(path.read_text(encoding="utf-8"))
            kind = data.get("kind")
            self.assertIn(kind, KIND_TO_SCHEMA, path.name)
            schema = json.loads((ROOT / "schemas" / KIND_TO_SCHEMA[kind]).read_text(encoding="utf-8"))
            errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda e: list(e.path))
            self.assertFalse(errors, f"{path.name}: {[e.message for e in errors]}")

    def test_complete_example_set_passes_normative_validator(self):
        proc = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(ROOT / "examples")],
            text=True, capture_output=True,
        )
        self.assertEqual(0, proc.returncode, proc.stdout + "\n" + proc.stderr)
        self.assertIn("PASSED", proc.stdout)

    def test_normative_specs_are_routed_and_chunked(self):
        yaml_parts = [
            "common.md", "config.md", "data.md", "view.md", "rule.md",
            "api.md", "enum.md", "system-information.md", "business.md", "cross-file.md",
        ]
        xml_parts = [
            "common.md", "config.md", "data.md", "view.md", "rule.md",
            "api.md", "enum.md", "system-information.md", "business.md",
        ]

        yaml_router = (ROOT / "references" / "dec-yaml-spec.md").read_text(encoding="utf-8")
        xml_router = (ROOT / "references" / "xml-compatibility-spec.md").read_text(encoding="utf-8")

        self.assertLessEqual(len(yaml_router.splitlines()), 80)
        self.assertLessEqual(len(xml_router.splitlines()), 80)

        for name in yaml_parts:
            path = ROOT / "references" / "yaml-spec" / name
            self.assertTrue(path.is_file(), name)
            self.assertLessEqual(len(path.read_text(encoding="utf-8").splitlines()), 260, name)
            self.assertIn(f"yaml-spec/{name}", yaml_router)

        for name in xml_parts:
            path = ROOT / "references" / "xml-mapping" / name
            self.assertTrue(path.is_file(), name)
            self.assertLessEqual(len(path.read_text(encoding="utf-8").splitlines()), 200, name)
            if name != "common.md":
                self.assertIn(f"xml-mapping/{name}", xml_router)

    def test_api_path_parameter_contract_is_validated(self):
        bad = {
            "kind": "api",
            "apis": [{
                "name": "getOrder", "system": "order", "url": "/orders/{id}", "method": "GET",
                "request": {"params": [{"name": "orderId", "in": "path", "type": "long", "required": True}]},
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "bad-api.yaml"
            p.write_text(yaml.safe_dump(bad, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(p)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("path variable", proc.stderr + proc.stdout)

    def test_information_recognizers_are_mutually_exclusive(self):
        bad = {
            "kind": "systems",
            "systems": [{
                "name": "order",
                "viewRefs": ["OrderInfo"],
                "information": [{
                    "name": "invalid",
                    "viewRef": "OrderInfo",
                    "ruleRef": "rv",
                    "ruleData": "status = 1",
                }],
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "bad.yaml"
            p.write_text(yaml.safe_dump(bad, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(p)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("exactly one", proc.stderr + proc.stdout)

    def test_api_regex_min_max_validation_round_trip(self):
        api = {
            "kind": "api",
            "apis": [{
                "name": "checkCode", "system": "order", "url": "/codes", "method": "POST",
                "request": {"params": [{
                    "name": "code", "in": "body", "type": "string",
                    "validations": [
                        {"type": "regex", "value": "^[A-Z0-9]{6,32}$"},
                        {"type": "minLength", "value": 6},
                        {"type": "maxLength", "value": 32},
                    ],
                }, {
                    "name": "amount", "in": "body", "type": "decimal",
                    "validations": [{"type": "min", "value": 0}, {"type": "max", "value": 10000}],
                }]},
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            y = root / "api.yaml"
            x = root / "api.xml"
            y2 = root / "api2.yaml"
            y.write_text(yaml.safe_dump(api, sort_keys=False), encoding="utf-8")
            proc = subprocess.run([sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(y)], text=True, capture_output=True)
            self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
            proc = subprocess.run([sys.executable, str(ROOT / "scripts" / "yaml_to_xml.py"), str(y), "-o", str(x)], text=True, capture_output=True)
            self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
            xml = x.read_text(encoding="utf-8")
            self.assertIn('type="regex"', xml)
            self.assertIn('type="min"', xml)
            self.assertIn('type="max"', xml)
            proc = subprocess.run([sys.executable, str(ROOT / "scripts" / "xml_to_yaml.py"), str(x), "-o", str(y2), "--id-mode", "none"], text=True, capture_output=True)
            self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
            back = yaml.safe_load(y2.read_text(encoding="utf-8"))
            vals = back["apis"][0]["request"]["params"][0]["validations"]
            self.assertTrue(any(v.get("type") == "regex" and v.get("value") == "^[A-Z0-9]{6,32}$" for v in vals))

    def test_api_expression_validation_requires_expression(self):
        bad = {
            "kind": "api",
            "apis": [{
                "name": "submit", "system": "order", "url": "/orders", "method": "POST",
                "request": {"validations": [{"type": "expression"}]},
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "bad-expression.yaml"
            p.write_text(yaml.safe_dump(bad, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(p)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("expression", proc.stderr + proc.stdout)

    def test_unknown_rel_enum_is_rejected(self):
        enum_doc = {
            "kind": "enum",
            "enums": [{"name": "OrderStatus", "values": [{"value": 1, "name": "OPEN"}]}],
        }
        api_doc = {
            "kind": "api",
            "apis": [{
                "name": "submit", "system": "order", "url": "/orders", "method": "POST",
                "request": {"params": [{"name": "status", "in": "body", "type": "int", "relEnum": "MissingEnum"}]},
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "enum.yaml").write_text(yaml.safe_dump(enum_doc, sort_keys=False), encoding="utf-8")
            (root / "api.yaml").write_text(yaml.safe_dump(api_doc, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(root)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("MissingEnum", proc.stderr + proc.stdout)

    def test_duplicate_rule_view_code_is_rejected(self):
        bad = {
            "kind": "rule",
            "ruleViews": [
                {"name": "a", "code": "SAME", "viewRef": "V", "rules": [{"name": "r1", "type": "check"}]},
                {"name": "b", "code": "SAME", "viewRef": "V", "rules": [{"name": "r2", "type": "check"}]},
            ],
        }
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "bad-rule.yaml"
            p.write_text(yaml.safe_dump(bad, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(p)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("code", proc.stderr + proc.stdout)

    def test_unknown_rule_datasource_is_rejected_when_config_loaded(self):
        config_doc = {
            "kind": "config",
            "dataSourceInfo": {"dataSources": [{"name": "data1", "type": "MySQL"}]},
        }
        rule_doc = {
            "kind": "rule",
            "ruleViews": [{
                "name": "rv", "code": "RV", "viewRef": "V", "dataSource": "missing",
                "rules": [{"name": "r", "type": "check", "property": "id", "pattern": "NOTNULL"}],
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "config.yaml").write_text(yaml.safe_dump(config_doc, sort_keys=False), encoding="utf-8")
            (root / "rule.yaml").write_text(yaml.safe_dump(rule_doc, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(root)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("missing", proc.stderr + proc.stdout)

    def test_rel_enum_and_inline_enum_double_source_is_rejected(self):
        enum_doc = {
            "kind": "enum",
            "enums": [{"name": "OrderStatus", "values": [{"value": 1, "name": "OPEN"}]}],
        }
        api_doc = {
            "kind": "api",
            "apis": [{
                "name": "submit", "system": "order", "url": "/orders", "method": "POST",
                "request": {"params": [{
                    "name": "status", "in": "body", "type": "int", "relEnum": "OrderStatus",
                    "validations": [{"type": "enum", "values": [1, 2]}],
                }]},
            }],
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "enum.yaml").write_text(yaml.safe_dump(enum_doc, sort_keys=False), encoding="utf-8")
            (root / "api.yaml").write_text(yaml.safe_dump(api_doc, sort_keys=False), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(root)],
                text=True, capture_output=True,
            )
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("relEnum", proc.stderr + proc.stdout)

    def test_view_system_must_resolve_when_systems_loaded(self):
        data_doc = {
            "kind": "data",
            "datas": [{"name": "order", "system": "order", "properties": {"id": "int"}}],
        }
        view_doc = {
            "kind": "view",
            "views": [{"name": "OrderInfo", "system": "missing", "targetMain": "order", "properties": {"id": "id"}}],
        }
        systems_doc = {"kind": "systems", "systems": [{"name": "order", "dataRefs": ["order"], "viewRefs": ["OrderInfo"]}]}
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name, doc in (("data.yaml", data_doc), ("view.yaml", view_doc), ("systems.yaml", systems_doc)):
                (root / name).write_text(yaml.safe_dump(doc, sort_keys=False), encoding="utf-8")
            proc = subprocess.run([sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(root)], text=True, capture_output=True)
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("View OrderInfo.system", proc.stdout + proc.stderr)
            self.assertIn("missing", proc.stdout + proc.stderr)

    def test_information_change_value_must_exist_in_bound_enum(self):
        docs = {
            "enum.yaml": {"kind": "enum", "enums": [{"name": "OrderStatus", "values": [{"value": 1, "name": "OPEN"}]}]},
            "data.yaml": {"kind": "data", "datas": [{"name": "order", "system": "order", "properties": {"status": "int"}, "tables": [{"name": "orders", "dataSource": "data1", "key": "status", "keyType": "set", "columns": {"status": {"ref": "status", "relEnum": "OrderStatus"}}}]}]},
            "view.yaml": {"kind": "view", "views": [{"name": "OrderInfo", "system": "order", "targetMain": "order", "properties": {"status": "status"}}]},
            "systems.yaml": {"kind": "systems", "systems": [{"name": "order", "dataRefs": ["order"], "viewRefs": ["OrderInfo"], "information": [{"name": "closed", "viewRef": "OrderInfo", "ruleData": "status = 2", "changeData": "status : 2;"}]}]},
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name, doc in docs.items():
                (root / name).write_text(yaml.safe_dump(doc, sort_keys=False), encoding="utf-8")
            proc = subprocess.run([sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(root)], text=True, capture_output=True)
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("value 2", proc.stdout + proc.stderr)
            self.assertIn("OrderStatus", proc.stdout + proc.stderr)

    def test_case_target_cannot_also_have_dependencies(self):
        bad = {
            "kind": "business",
            "business": {"name": "b", "directories": [
                {"name": "result", "informationRef": "s.result", "modelRef": "V", "isRoot": True,
                 "subDirectories": [{"rel": "success", "role": "case", "informationRef": "s.success"}]},
                {"name": "success", "informationRef": "s.success", "modelRef": "V",
                 "dependencies": [{"informationRef": "s.extra"}]},
            ]},
        }
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "bad-business.yaml"
            p.write_text(yaml.safe_dump(bad, sort_keys=False), encoding="utf-8")
            proc = subprocess.run([sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(p)], text=True, capture_output=True)
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("case Directory target", proc.stdout + proc.stderr)
            self.assertIn("dependencies", proc.stdout + proc.stderr)


if __name__ == "__main__":
    unittest.main()
