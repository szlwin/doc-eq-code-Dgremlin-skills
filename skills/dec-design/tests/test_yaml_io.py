import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "yaml_io.py"


class YamlIoTest(unittest.TestCase):
    def test_write_read_merge(self):
        with tempfile.TemporaryDirectory() as td:
            td = Path(td)
            source = td / "source.json"
            source.write_text(json.dumps({"kind":"api","version":"dec/v1","apis":[]}), encoding="utf-8")
            out = td / "x.yaml"
            subprocess.run(["python3", str(SCRIPT), "write", str(out), "--from-json", str(source)], check=True, capture_output=True, text=True)
            got = subprocess.run(["python3", str(SCRIPT), "read", str(out)], check=True, capture_output=True, text=True)
            self.assertEqual(json.loads(got.stdout)["kind"], "api")
            patch = td / "patch.json"
            patch.write_text(json.dumps({"version":"dec/v2"}), encoding="utf-8")
            subprocess.run(["python3", str(SCRIPT), "merge", str(out), "--patch-json", str(patch)], check=True, capture_output=True, text=True)
            got2 = subprocess.run(["python3", str(SCRIPT), "read", str(out)], check=True, capture_output=True, text=True)
            self.assertEqual(json.loads(got2.stdout)["version"], "dec/v2")

    def test_reject_non_yaml_output(self):
        with tempfile.TemporaryDirectory() as td:
            td = Path(td)
            src = td / "p.json"
            src.write_text("{}", encoding="utf-8")
            proc = subprocess.run(["python3", str(SCRIPT), "write", str(td / "x.xml"), "--from-json", str(src)], capture_output=True, text=True)
            self.assertNotEqual(proc.returncode, 0)


if __name__ == "__main__":
    unittest.main()
