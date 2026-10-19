import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from safe_xml import load_safe_xml, parse_safe_xml, MAX_XML_BYTES


class XmlSecurityTest(unittest.TestCase):
    def test_in_memory_parser_and_sync_projection_use_the_same_guard(self):
        from unittest.mock import patch
        from sync_xml_to_yaml import _project_existing
        malicious = '<!DOCTYPE root [<!ENTITY x "expanded">]><root>&x;</root>'
        for content in (malicious, malicious.encode("utf-8"), malicious.encode("utf-16")):
            with self.assertRaisesRegex(ValueError, "DTD and ENTITY"):
                parse_safe_xml(content)
        with patch("sync_xml_to_yaml.yaml_to_xml_document", return_value=malicious):
            with self.assertRaisesRegex(ValueError, "DTD and ENTITY"):
                _project_existing({})
        with self.assertRaisesRegex(ValueError, "exceeds"):
            parse_safe_xml(b" " * (MAX_XML_BYTES + 1))
        self.assertEqual("root", parse_safe_xml('<root><!-- <!DOCTYPE text> --></root>').documentElement.tagName)

    def test_in_memory_unicode_keeps_decoded_text_regardless_of_encoding_declaration(self):
        for encoding in ("UTF-8", "UTF-16", "ISO-8859-1"):
            content = f'<?xml version="1.0" encoding="{encoding}"?><root>é中</root>'
            with self.subTest(encoding=encoding):
                self.assertEqual("é中", parse_safe_xml(content).documentElement.firstChild.data)
        for encoding in ("utf-8", "utf-16"):
            content = f'<?xml version="1.0" encoding="{encoding}"?><root>é中</root>'
            self.assertEqual("é中", parse_safe_xml(content.encode(encoding)).documentElement.firstChild.data)

    def test_import_sync_and_inspection_reject_dtd_before_expansion(self):
        with tempfile.TemporaryDirectory() as tmp:
            xml = Path(tmp) / "external.xml"
            for declaration in ('<!DOCTYPE rule-view>',
                    '<!DOCTYPE rule-view [<!ENTITY x "expanded">]>',
                    '<!DOCTYPE rule-view SYSTEM "file:///must-not-read">'):
                for encoding in ("utf-8", "utf-16"):
                    xml.write_bytes((f'<?xml version="1.0" encoding="{encoding}"?>'+declaration+'<rule-view/>').encode(encoding))
                    commands = [
                        [ROOT / "scripts/xml_to_yaml.py", xml, "--check"],
                        [ROOT / "scripts/sync_xml_to_yaml.py", xml, "--existing", ROOT / "examples/rule-order.yaml", "--check"],
                        [ROOT.parent / "dec-implementation/scripts/xml_io.py", "check", xml],
                        [ROOT.parent / "dec-implementation/scripts/xml_io.py", "read", xml],
                    ]
                    for command in commands:
                        result = subprocess.run([sys.executable, *map(str, command)], capture_output=True, text=True)
                        self.assertNotEqual(0, result.returncode, command)
                        self.assertIn("DTD and ENTITY", result.stdout + result.stderr)

    def test_size_limit_and_literal_markup(self):
        with tempfile.TemporaryDirectory() as tmp:
            xml = Path(tmp) / "safe.xml"
            xml.write_text('<root><!-- <!DOCTYPE innocent> --><![CDATA[<!ENTITY text>]]><x>&amp;</x></root>')
            self.assertEqual("root", load_safe_xml(xml).documentElement.tagName)
            xml.write_bytes(b" " * (MAX_XML_BYTES + 1))
            with self.assertRaisesRegex(ValueError, "exceeds"):
                load_safe_xml(xml)

    def test_independent_skills_ship_identical_security_helper(self):
        self.assertEqual((ROOT / "scripts/safe_xml.py").read_bytes(),
                         (ROOT.parent / "dec-implementation/scripts/safe_xml.py").read_bytes())
