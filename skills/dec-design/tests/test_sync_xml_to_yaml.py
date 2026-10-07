import copy
import sys
import tempfile
import unittest
from pathlib import Path
from xml.dom import minidom

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from sync_xml_to_yaml import sync_document
from yaml_to_xml import convert_document as yaml_to_xml, load_yaml


class SyncXmlToYamlTest(unittest.TestCase):
    def test_xml_change_updates_representable_field_and_preserves_yaml_only_metadata(self):
        existing = load_yaml(ROOT / "examples" / "rule-order.yaml")
        existing = copy.deepcopy(existing)
        existing["notes"] = "top-level yaml-only note"
        existing["ruleViews"][0]["notes"] = "rule-view yaml-only note"

        xml = yaml_to_xml(existing, emit_id_comments=True)
        xml = xml.replace('desc="保存并提交订单的业务规则视图"', 'desc="保存并校验订单"', 1)
        merged, report = sync_document(existing, minidom.parseString(xml))

        self.assertEqual("READY", report["status"])
        self.assertEqual("保存并校验订单", merged["ruleViews"][0]["desc"])
        self.assertEqual("top-level yaml-only note", merged["notes"])
        self.assertEqual("rule-view yaml-only note", merged["ruleViews"][0]["notes"])
        self.assertEqual("RV-SAVE-ORDER", merged["ruleViews"][0]["id"])

    def test_xml_without_id_comments_does_not_replace_existing_design_ids(self):
        existing = load_yaml(ROOT / "examples" / "rule-order.yaml")
        xml = yaml_to_xml(existing, emit_id_comments=False)
        xml = xml.replace('desc="保存并提交订单的业务规则视图"', 'desc="外部 XML 修改"', 1)
        merged, report = sync_document(existing, minidom.parseString(xml))

        self.assertEqual("READY", report["status"])
        self.assertEqual("RV-SAVE-ORDER", merged["ruleViews"][0]["id"])
        self.assertEqual("外部 XML 修改", merged["ruleViews"][0]["desc"])

    def test_entity_delete_is_blocked_by_default(self):
        existing = {
            "kind": "rule",
            "version": "dec/v1",
            "ruleViews": [{
                "id": "RV-ONE",
                "name": "one",
                "code": "ONE",
                "viewRef": "OrderInfo",
                "rules": [
                    {"id": "RULE-A", "name": "a", "type": "check", "property": "id", "pattern": "NOTNULL"},
                    {"id": "RULE-B", "name": "b", "type": "check", "property": "id", "pattern": "NOTNULL"},
                ],
            }],
        }
        xml = yaml_to_xml(existing, emit_id_comments=True)
        doc = minidom.parseString(xml)
        rules = doc.getElementsByTagName("rule")
        rules[1].parentNode.removeChild(rules[1])

        merged, report = sync_document(existing, doc, allow_delete=False)
        self.assertEqual("CONFLICT", report["status"])
        self.assertEqual(2, len(merged["ruleViews"][0]["rules"]))
        self.assertTrue(any(c.get("code") == "ENTITY_DELETE_REQUIRES_ALLOW_DELETE" for c in report["conflicts"]))

    def test_entity_delete_can_be_explicitly_allowed(self):
        existing = {
            "kind": "rule",
            "version": "dec/v1",
            "ruleViews": [{
                "id": "RV-ONE",
                "name": "one",
                "code": "ONE",
                "viewRef": "OrderInfo",
                "rules": [
                    {"id": "RULE-A", "name": "a", "type": "check", "property": "id", "pattern": "NOTNULL"},
                    {"id": "RULE-B", "name": "b", "type": "check", "property": "id", "pattern": "NOTNULL"},
                ],
            }],
        }
        xml = yaml_to_xml(existing, emit_id_comments=True)
        doc = minidom.parseString(xml)
        rules = doc.getElementsByTagName("rule")
        rules[1].parentNode.removeChild(rules[1])

        merged, report = sync_document(existing, doc, allow_delete=True)
        self.assertEqual("READY", report["status"])
        self.assertEqual(["a"], [r["name"] for r in merged["ruleViews"][0]["rules"]])

    def test_rename_with_dec_id_comment_is_unambiguous(self):
        existing = load_yaml(ROOT / "examples" / "rule-order.yaml")
        xml = yaml_to_xml(existing, emit_id_comments=True)
        xml = xml.replace('name="save-Order"', 'name="saveOrderV2"', 1)
        merged, report = sync_document(existing, minidom.parseString(xml))

        self.assertEqual("READY", report["status"])
        self.assertEqual("RV-SAVE-ORDER", merged["ruleViews"][0]["id"])
        self.assertEqual("saveOrderV2", merged["ruleViews"][0]["name"])

    def test_rename_without_dec_id_comment_fails_closed(self):
        existing = load_yaml(ROOT / "examples" / "rule-order.yaml")
        xml = yaml_to_xml(existing, emit_id_comments=False)
        xml = xml.replace('name="save-Order"', 'name="saveOrderV2"', 1)
        merged, report = sync_document(existing, minidom.parseString(xml))

        self.assertEqual("CONFLICT", report["status"])
        self.assertTrue(any(c.get("code") == "AMBIGUOUS_RENAME_OR_REPLACEMENT" for c in report["conflicts"]))
        self.assertEqual("save-Order", merged["ruleViews"][0]["name"])


if __name__ == "__main__":
    unittest.main()
