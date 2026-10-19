import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate_binding.py"


class BindingValidatorTest(unittest.TestCase):
    def test_file_only_validation_rejects_path_escape_without_symbol_flag(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); project = root / "project"; project.mkdir()
            outside = root / "outside.java"; outside.write_text("class Outside {}")
            (project / "link.java").symlink_to(outside)
            (root / "design.yaml").write_text("kind: data\ndatas: [{id: DATA-X, name: x}]\n")
            for filename in ("../outside.java", "link.java", str(outside)):
                (root / "binding.yaml").write_text("version: dec-binding/v1\nbindings:\n"
                    "  - designId: DATA-X\n    strategy: REUSE\n    status: PLANNED\n"
                    f"    implementation:\n      - module: app\n        file: {filename}\n        symbol: Outside\n")
                result = subprocess.run([sys.executable, str(SCRIPT), "--design", str(root / "design.yaml"),
                    "--binding", str(root / "binding.yaml"), "--project-root", str(project)], capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("escapes project root", result.stdout + result.stderr)

    def test_require_ready_rejects_planned_binding_even_when_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "design.yaml").write_text(
                "kind: data\ndatas:\n  - id: DATA-X\n    name: x\n    properties: {id: int}\n",
                encoding="utf-8",
            )
            (t / "binding.yaml").write_text(
                "version: dec-binding/v1\nbindings:\n  - designId: DATA-X\n    strategy: REUSE\n    status: PLANNED\n",
                encoding="utf-8",
            )
            base = [sys.executable, str(SCRIPT), "--design", str(t / "design.yaml"),
                    "--binding", str(t / "binding.yaml")]
            complete = subprocess.run(base + ["--require-complete"], text=True, capture_output=True)
            self.assertEqual(0, complete.returncode, complete.stdout + complete.stderr)
            self.assertIn("WARN DATA-X: binding remains PLANNED", complete.stdout)
            ready = subprocess.run(base + ["--require-ready"], text=True, capture_output=True)
            self.assertNotEqual(0, ready.returncode)
            self.assertIn("release readiness requires VERIFIED", ready.stdout + ready.stderr)

    def test_require_complete_reports_unbound_design_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "design.yaml").write_text(
                "kind: data\ndatas:\n  - id: DATA-X\n    name: x\n    properties: {id: int}\n",
                encoding="utf-8",
            )
            (t / "binding.yaml").write_text(
                "version: dec-binding/v1\nbindings: []\n", encoding="utf-8"
            )
            proc = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--design",
                    str(t / "design.yaml"),
                    "--binding",
                    str(t / "binding.yaml"),
                    "--require-complete",
                ],
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(proc.returncode, 0)
            self.assertIn("missing binding for designId DATA-X", proc.stdout + proc.stderr)

    def test_html_recovery_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "design.yaml").write_text(
                "artifact:\n  producer: html-recovery\nsourceText: page\n",
                encoding="utf-8",
            )
            (t / "binding.yaml").write_text(
                "version: dec-binding/v1\nbindings: []\n", encoding="utf-8"
            )
            proc = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--design",
                    str(t / "design.yaml"),
                    "--binding",
                    str(t / "binding.yaml"),
                ],
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(proc.returncode, 0)
            self.assertIn("HTML recovery artifact", proc.stdout + proc.stderr)

    def test_known_design_id_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "design.yaml").write_text("""
kind: business
business:
  id: BUS-X
  name: x
  directories:
    - id: ACT-START-PAY
      name: y
      informationRef: x.y
      modelRef: X
      isRoot: true
      actions:
        - id: INFO-ORDER-PAYABLE
          name: z
""", encoding="utf-8")
            (t / "enum.yaml").write_text("""
kind: enum
enums:
  - id: ENUM-ORDER-STATUS
    name: OrderStatus
    values:
      - value: 1
        name: OPEN
""", encoding="utf-8")
            proc = subprocess.run([
                sys.executable, str(SCRIPT), "--design", str(t),
                "--binding", str(ROOT / "examples" / "implementation-binding.yaml")
            ], text=True, capture_output=True)
            self.assertEqual(proc.returncode, 0, proc.stderr)

    def test_non_design_id_values_are_not_collected(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "design.yaml").write_text("""
kind: data
datas:
  - id: DATA-ORDER
    name: order
    properties:
      id: int
      status: int
""", encoding="utf-8")
            (t / "binding.yaml").write_text("""
version: dec-binding/v1
bindings:
  - designId: DATA-ORDER
    strategy: REUSE
    status: VERIFIED
    runtimeManaged: true
    runtimeCapability: data model
""", encoding="utf-8")
            proc = subprocess.run([
                sys.executable, str(SCRIPT), "--design", str(t / "design.yaml"),
                "--binding", str(t / "binding.yaml")
            ], text=True, capture_output=True)
            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn("designIds=1", proc.stdout)

    def test_enum_design_id_is_bindable(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "enum.yaml").write_text("""
kind: enum
enums:
  - id: ENUM-ORDER-STATUS
    name: OrderStatus
    values:
      - id: ENUM-VALUE-ORDER-STATUS-OPEN
        value: 1
        name: OPEN
""", encoding="utf-8")
            (t / "binding.yaml").write_text("""
version: dec-binding/v1
bindings:
  - designId: ENUM-ORDER-STATUS
    strategy: REUSE
    status: VERIFIED
    runtimeManaged: true
    runtimeCapability: enum registry
""", encoding="utf-8")
            proc = subprocess.run([
                sys.executable, str(SCRIPT), "--design", str(t),
                "--binding", str(t / "binding.yaml")
            ], text=True, capture_output=True)
            self.assertEqual(proc.returncode, 0, proc.stderr)


if __name__ == "__main__":
    unittest.main()
