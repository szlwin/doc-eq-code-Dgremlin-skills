import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import check_manifest


class ManifestTest(unittest.TestCase):
    def test_source_changes_and_assets_change_digest_but_generated_outputs_do_not(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(check_manifest, "ROOT", Path(tmp)):
            root = Path(tmp)
            asset = root / "scripts/render_assets/design.js"
            asset.parent.mkdir(parents=True)
            asset.write_text("first")
            first = check_manifest.expected()
            self.assertIn("scripts/render_assets/design.js", first)
            generated = root / "examples/generated-html/index.html"
            generated.parent.mkdir(parents=True)
            generated.write_text("ignored")
            self.assertEqual(first, check_manifest.expected())
            asset.write_text("changed")
            self.assertNotEqual(first, check_manifest.expected())
