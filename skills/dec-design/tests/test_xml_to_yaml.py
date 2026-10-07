import sys
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.dom import minidom

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from xml_to_yaml import ConversionError as XmlToYamlError, convert_document as xml_to_yaml, load_xml
from yaml_to_xml import convert_document as yaml_to_xml, load_yaml


def normalize_xml(text: str):
    root = ET.fromstring(text)

    def norm(el):
        return (
            el.tag,
            tuple(sorted(el.attrib.items())),
            (el.text or "").strip("\n"),
            tuple(norm(c) for c in list(el)),
        )

    return norm(root)


class XmlToYamlTest(unittest.TestCase):
    def test_all_generated_xml_roundtrip_semantically(self):
        for path in sorted((ROOT / "examples" / "generated-xml").glob("*.xml")):
            source = path.read_text(encoding="utf-8")
            canonical = xml_to_yaml(load_xml(path), id_mode="auto")
            regenerated = yaml_to_xml(canonical)
            self.assertEqual(normalize_xml(source), normalize_xml(regenerated), path.name)

    def test_generated_yaml_validates_as_a_complete_set(self):
        import subprocess

        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            for path in sorted((ROOT / "examples" / "generated-xml").glob("*.xml")):
                canonical = xml_to_yaml(load_xml(path), id_mode="auto")
                import yaml
                from xml_to_yaml import dump_yaml
                (out / path.with_suffix(".yaml").name).write_text(dump_yaml(canonical), encoding="utf-8")
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(out)],
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, proc.returncode, proc.stdout + "\n" + proc.stderr)
            self.assertIn("PASSED", proc.stdout)

    def test_id_comments_restore_exact_ids(self):
        source = load_yaml(ROOT / "examples" / "business-order-payment.yaml")
        xml = yaml_to_xml(source, emit_id_comments=True)
        restored = xml_to_yaml(minidom.parseString(xml), id_mode="auto")
        self.assertEqual(source["business"]["id"], restored["business"]["id"])
        self.assertEqual(
            [x["id"] for x in source["business"]["directories"]],
            [x["id"] for x in restored["business"]["directories"]],
        )
        self.assertEqual(
            source["business"]["directories"][0]["actions"][0]["id"],
            restored["business"]["directories"][0]["actions"][0]["id"],
        )

    def test_without_comments_auto_generates_deterministic_ids(self):
        xml = """<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<orm-rule-mapping>
  <rule-view-info name=\"saveOrder\" view-ref=\"OrderInfo\">
    <rule name=\"insertOrder\" type=\"insert\" property=\"OrderInfo\"/>
  </rule-view-info>
</orm-rule-mapping>
"""
        first = xml_to_yaml(minidom.parseString(xml), id_mode="auto")
        second = xml_to_yaml(minidom.parseString(xml), id_mode="auto")
        self.assertEqual(first, second)
        self.assertEqual("RV-SAVEORDER", first["ruleViews"][0]["id"])
        self.assertEqual("saveOrder", first["ruleViews"][0]["code"])
        self.assertEqual("RULE-SAVEORDER-INSERTORDER", first["ruleViews"][0]["rules"][0]["id"])

    def test_comments_mode_does_not_invent_ids(self):
        xml = """<orm-data-mapping><data name=\"order\"><property-info><property name=\"id\" type=\"int\"/></property-info></data></orm-data-mapping>"""
        data = xml_to_yaml(minidom.parseString(xml), id_mode="comments")
        self.assertNotIn("id", data["datas"][0])

    def test_legacy_data_root_typo_is_imported_but_normalized(self):
        xml = """<orm--data-mapping><data name=\"order\"><property-info><property name=\"id\" type=\"int\"/></property-info></data></orm--data-mapping>"""
        data = xml_to_yaml(minidom.parseString(xml), id_mode="none")
        data["datas"][0]["system"] = "legacy"
        regenerated = yaml_to_xml(data)
        self.assertIn("<orm-data-mapping>", regenerated)
        self.assertNotIn("<orm--data-mapping>", regenerated)

    def test_legacy_standalone_directory_rejected(self):
        with self.assertRaises(XmlToYamlError):
            xml_to_yaml(minidom.parseString("<directory-config/>"))


if __name__ == "__main__":
    unittest.main()
