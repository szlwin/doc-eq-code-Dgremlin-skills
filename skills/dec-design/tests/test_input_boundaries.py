import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class InputBoundaryTest(unittest.TestCase):
    def test_html_recovery_artifact_is_rejected_with_actionable_message(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "recovered.yaml"
            path.write_text(
                "artifact:\n  producer: html-recovery\nsourceText: page\n",
                encoding="utf-8",
            )
            proc = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "validate_dec_yaml.py"), str(path)],
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(proc.returncode, 0)
            self.assertIn("HTML recovery artifact", proc.stdout + proc.stderr)


if __name__ == "__main__":
    unittest.main()
