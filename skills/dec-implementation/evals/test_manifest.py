"""评测源码完整性与排除 evals 后独立安装的回归。"""
import contextlib
import importlib.util
import io
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SKILL_ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("eval_manifest", SKILL_ROOT / "evals/check_manifest.py")
manifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)


class EvalManifestTest(unittest.TestCase):
    def test_add_modify_delete_and_missing_manifest_are_detected(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(manifest, "ROOT", Path(directory)), contextlib.redirect_stdout(io.StringIO()):
            root = Path(directory)
            source = root / "judge.py"
            source.write_text("original")
            self.assertEqual(1, manifest.main([]))
            self.assertEqual(0, manifest.main(["--write"]))
            self.assertEqual(0, manifest.main([]))
            source.write_text("changed")
            self.assertEqual(1, manifest.main([]))
            manifest.main(["--write"])
            added = root / "new_case.json"
            added.write_text("{}")
            self.assertEqual(1, manifest.main([]))
            manifest.main(["--write"])
            added.unlink()
            self.assertEqual(1, manifest.main([]))

    def test_fixture_changes_are_tracked_but_run_outputs_are_not(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(manifest, "ROOT", Path(directory)):
            root = Path(directory)
            fixture = root / "fixtures/example/source.json"
            fixture.parent.mkdir(parents=True)
            fixture.write_text("original")
            before = manifest.expected()
            self.assertIn("fixtures/example/source.json", before)
            for name in ("__pycache__/judge.pyc", "iteration-1/result.json", "reports/report.html", "outputs/result.json", ".DS_Store"):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("generated")
            self.assertEqual(before, manifest.expected())
            fixture.write_text("changed")
            self.assertNotEqual(before, manifest.expected())

    def test_installation_without_evals_still_passes_skill_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            installed = Path(directory) / SKILL_ROOT.name
            shutil.copytree(SKILL_ROOT, installed, ignore=shutil.ignore_patterns("evals", "__pycache__"))
            self.assertFalse((installed / "evals").exists())
            result = subprocess.run([sys.executable, str(installed / "scripts/check_manifest.py")], text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
