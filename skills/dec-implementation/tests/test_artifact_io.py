import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
YAML_IO = ROOT / "scripts" / "yaml_io.py"
XML_IO = ROOT / "scripts" / "xml_io.py"


class ArtifactIoTest(unittest.TestCase):
    def test_yaml_io(self):
        with tempfile.TemporaryDirectory() as td:
            td = Path(td)
            payload = td / "p.json"
            payload.write_text(json.dumps({"version":"dec-binding/v1","bindings":[]}), encoding="utf-8")
            out = td / "binding.yaml"
            subprocess.run(["python3", str(YAML_IO), "write", str(out), "--from-json", str(payload)], check=True, capture_output=True, text=True)
            read = subprocess.run(["python3", str(YAML_IO), "read", str(out)], check=True, capture_output=True, text=True)
            self.assertEqual(json.loads(read.stdout)["version"], "dec-binding/v1")

    def test_xml_io_read_only(self):
        with tempfile.TemporaryDirectory() as td:
            f = Path(td) / "x.xml"
            f.write_text('<root a="1"><child>v</child></root>', encoding="utf-8")
            chk = subprocess.run(["python3", str(XML_IO), "check", str(f)], check=True, capture_output=True, text=True)
            self.assertIn("PASSED", chk.stdout)
            read = subprocess.run(["python3", str(XML_IO), "read", str(f)], check=True, capture_output=True, text=True)
            self.assertEqual(json.loads(read.stdout)["tag"], "root")


if __name__ == "__main__":
    unittest.main()
