import sys
import unittest
from pathlib import Path
from xml.dom import minidom

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from yaml_to_xml import ConversionError, convert_document as export_xml
from xml_to_yaml import convert_document as import_xml


class RuleContractTest(unittest.TestCase):
    @staticmethod
    def document(rules):
        return {"kind": "rule", "version": "dec/v1", "ruleViews": [{
            "id": "RV-TEST", "name": "test", "code": "TEST", "viewRef": "V",
            "dataSource": "db", "rules": rules,
        }]}

    def test_rule_metadata_and_dsl_roundtrip(self):
        rules = [
            {"id": "R-READ", "name": "read", "type": "checkDataPattern", "property": "items",
             "cmd": "select a.id as u_id from items a where a.id = #id", "pattern": "u_id = id",
             "dataSource": "secondary", "error": {"code": "C001", "message": "用户错误", "level": 1},
             "customer": {"mode": "strict", "retry": "2"}},
            {"id": "R-DSL", "name": "calculate", "type": "dsl",
             "process": "#num : if totalPrice>10 then totalPrice*1.1 else totalPrice*1.2; totalPrice : totalPrice*#num;"},
        ]
        restored = import_xml(minidom.parseString(export_xml(self.document(rules), emit_id_comments=True)))
        self.assertEqual(rules, restored["ruleViews"][0]["rules"])

    def test_customer_rejects_lossy_xml_attribute_values(self):
        for value in ({"nested": "value"}, ["value"], 1, True, None):
            with self.subTest(value=value), self.assertRaises(ConversionError):
                export_xml(self.document([{"name": "q", "type": "query", "property": "items",
                                           "customer": {"mode": value}}]))

    def test_conflicting_dsl_aliases_fail(self):
        with self.assertRaises(ConversionError):
            export_xml(self.document([{"name": "d", "type": "dsl", "process": "x:1;", "grammer": "x:2;"}]))


if __name__ == "__main__":
    unittest.main()
