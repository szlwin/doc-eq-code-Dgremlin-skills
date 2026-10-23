import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import check_manifest


class ManifestTest(unittest.TestCase):
    def test_source_changes_and_generated_outputs_change_digest_only_when_tracked(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(check_manifest, "ROOT", Path(tmp)):
            root = Path(tmp)
            tracked = root / "scripts/example.py"
            tracked.parent.mkdir(parents=True)
            tracked.write_text("first")
            first = check_manifest.expected()
            self.assertIn("scripts/example.py", first)
            generated = root / "target/output.txt"
            generated.parent.mkdir(parents=True)
            generated.write_text("ignored")
            self.assertEqual(first, check_manifest.expected())
            tracked.write_text("changed")
            self.assertNotEqual(first, check_manifest.expected())

    def test_main_detects_and_accepts_the_tracked_manifest(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(check_manifest, "ROOT", Path(tmp)):
            root = Path(tmp)
            tracked = root / "README.md"
            tracked.write_text("first")
            check_manifest.MANIFEST = root / "FILE_MANIFEST.sha256"
            check_manifest.MANIFEST.write_text(check_manifest.expected())
            self.assertEqual(0, check_manifest.main([]))
            tracked.write_text("changed")
            self.assertEqual(1, check_manifest.main([]))
