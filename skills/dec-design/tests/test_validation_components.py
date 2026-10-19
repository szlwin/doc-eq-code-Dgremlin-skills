import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from dec_common import resolve_view_property_enums
from validation_common import ValidationContext
from validation_local import validate_api
from validation_information import validate_information_changes
from render_catalog import resolve_view_property_enums as render_enums
from validate_dec_yaml import validate


class ValidationComponentsTest(unittest.TestCase):
    def test_api_path_contract_is_testable_without_files_or_cli(self):
        ctx = ValidationContext(Path("design"))
        validate_api(Path("api.yaml"), {"apis": [{"name": "get", "system": "order", "url": "/orders/{id}",
            "method": "GET", "request": {"params": [{"name": "id", "in": "path", "type": "long", "required": False}]}}]}, ctx)
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("path parameter must set required", ctx.errors[0]["message"])

    def test_nested_named_list_mapping_and_ambiguous_enum_are_shared_with_html(self):
        datas = {"Line": {"tables": [{"columns": [
            {"name": "state_code", "refProperty": "state", "relEnum": "LineState"}]}]}}
        view = {"targetMain": "Order", "properties": [{"name": "lines", "relation": "one-to-many", "data": "Line",
                "properties": [{"name": "status", "ref": "state"}]}]}
        self.assertEqual({"LineState"}, resolve_view_property_enums(view, "lines.status", datas))
        self.assertEqual({"LineState"}, render_enums({"views": {"OrderInfo": view}, "datas": datas}, "OrderInfo", "lines.status"))
        datas["Line"]["tables"].append({"columns": {"state": {"ref": "state", "relEnum": "OtherState"}}})
        ctx = ValidationContext(Path("design"))
        ctx.data_defs = {k: (Path("data.yaml"), v) for k, v in datas.items()}
        ctx.view_defs = {"OrderInfo": (Path("view.yaml"), view)}
        ctx.information_defs = {"order.done": (Path("system.yaml"), {"viewRef": "OrderInfo",
            "changeData": "every(lines, status: 1);"}, "order")}
        validate_information_changes(ctx)
        self.assertEqual(1, len(ctx.errors))
        self.assertIn("maps to multiple Enums", ctx.errors[0]["message"])
        self.assertEqual(set(), resolve_view_property_enums(view, "lines..status", datas))

    def test_empty_input_is_not_success_and_context_does_not_leak(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual("FAILED", validate(Path(tmp))["status"])
        self.assertEqual("PASSED", validate(ROOT / "examples")["status"])
