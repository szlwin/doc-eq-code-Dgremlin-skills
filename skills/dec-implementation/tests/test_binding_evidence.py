import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
import binding_evidence as evidence
import run_binding_verification as runner
import validate_binding as validator


class BindingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.root = self.base / "app"
        self.root.mkdir()
        self.design = self.base / "design.yaml"
        self.binding = self.base / "binding.yaml"
        self.design.write_text('kind: data\ndatas: [{id: DATA-X, name: x, properties: {id: int}}]\n')
        (self.root / "src").mkdir()
        (self.root / "src/Thing.java").write_text('''package example;
class Thing {
  String misleading = "void missing() {}";
  // void deleted() {}
  void work() { helper(); }
  void helper() {}
  void overload() {}
  void overload(String value) {}
  static class Nested { void run() {} }
}
class Decoy { void other() {} }
''')
        (self.root / "src/ThingTest.java").write_text('''package example;
class ThingTest { void works() {} void parameterized(int value) {} }
''')
        self.doc = {"version": "dec-binding/v1", "bindings": [{"designId": "DATA-X", "strategy": "REUSE", "status": "VERIFIED",
                    "implementation": [{"file": "src/Thing.java", "symbol": "Thing#work"}],
                    "tests": [{"file": "src/ThingTest.java", "symbol": "ThingTest#works"}]}]}
        self.write_binding()
        self.proof = self.root / "target/dec-binding-evidence.json"
        self.report = self.root / "target/surefire-reports/TEST-example.ThingTest.xml"
        self.args = ["--design", str(self.design), "--binding", str(self.binding), "--project-root", str(self.root)]

    def write_binding(self):
        # JSON is a YAML subset; test fixtures use the same serialized object as the validator input.
        self.binding.write_text(json.dumps(self.doc))

    def validate(self, *extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            rc = validator.main(self.args + list(extra) + ["--json"])
        return rc, out.getvalue()

    def run_tests(self, xml=None, code=None, extra=None):
        if xml is None:
            xml = '<testsuite tests="1" failures="0"><testcase classname="example.ThingTest" name="works"/></testsuite>'
        if code is None:
            code = "from pathlib import Path; p=Path('target/surefire-reports/TEST-example.ThingTest.xml'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(" + repr(xml) + ")"
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            rc = runner.main(self.args + (extra or []) + ["--", sys.executable, "-c", code])
        return rc, out.getvalue()

    def test_current_successful_run_proves_ready_and_records_digests(self):
        rc, out = self.run_tests()
        self.assertEqual(0, rc, out)
        rc, out = self.validate("--require-ready")
        self.assertEqual(0, rc, out)
        result = json.loads(out)
        self.assertTrue(result["coverageComplete"])
        self.assertTrue(result["releaseReady"])
        self.assertTrue(result["evidenceVerified"])
        proof = json.loads(self.proof.read_text())
        self.assertEqual(evidence.design_digest(self.design), proof["designDigest"])
        self.assertEqual(evidence.file_digest(self.report), proof["reports"][0]["digest"])

    def test_report_dtd_is_rejected_for_all_supported_encodings(self):
        self.report.parent.mkdir(parents=True)
        for encoding in ("utf-8", "utf-16", "utf-16-le", "utf-16-be"):
            declaration = "utf-16" if encoding.startswith("utf-16") else encoding
            xml = ('<?xml version="1.0" encoding="' + declaration + '"?>'
                   '<!DOCTYPE testsuite [<!ENTITY n "works">]>'
                   '<testsuite><testcase classname="example.ThingTest" name="&n;"/></testsuite>')
            with self.subTest(encoding=encoding):
                self.report.write_bytes(xml.encode(encoding))
                with self.assertRaisesRegex(ValueError, "DTD and ENTITY"):
                    evidence.junit_cases(self.report)

    def test_report_safety_preserves_comments_and_rejects_oversized_inputs(self):
        from safe_xml import MAX_XML_BYTES
        self.report.parent.mkdir(parents=True)
        self.report.write_text('<testsuite><!-- <!DOCTYPE innocent> -->'
                               '<testcase classname="example.ThingTest" name="works">'
                               '<system-out><![CDATA[<!ENTITY text>]]></system-out>'
                               '</testcase></testsuite>')
        self.assertTrue(evidence.junit_cases(self.report)[0]["passed"])
        self.report.write_bytes(b" " * (MAX_XML_BYTES + 1))
        with self.assertRaisesRegex(ValueError, "exceeds"):
            evidence.junit_cases(self.report)

    def test_runner_rejects_default_output_through_external_target_symlink(self):
        outside = self.base / "outside"
        outside.mkdir()
        (self.root / "target").symlink_to(outside, target_is_directory=True)
        marker = outside / "dec-binding-evidence.json"
        marker.write_text("must survive")
        rc, out = self.run_tests(code="raise AssertionError('must not run')")
        self.assertNotEqual(0, rc, out)
        self.assertIn("evidence output must be inside", out)
        self.assertEqual("must survive", marker.read_text())

    def test_runner_rejects_escaped_report_directories_and_files(self):
        for report_dir in ("../outside", str(self.base / "outside")):
            rc, out = self.run_tests(extra=["--report-dir", report_dir])
            self.assertNotEqual(0, rc, out)
            self.assertIn("report directory escapes", out)
            self.assertFalse(self.proof.exists())
        outside = self.base / "outside.xml"
        outside.write_text('<testsuite><testcase classname="example.ThingTest" name="works"/></testsuite>')
        self.report.unlink()
        self.report.symlink_to(outside)
        rc, out = self.run_tests(code="pass")
        self.assertNotEqual(0, rc, out)
        self.assertIn("report escapes project root", out)
        self.assertFalse(self.proof.exists())

    def test_runner_does_not_follow_existing_pending_output_symlink(self):
        self.proof.parent.mkdir(parents=True)
        outside = self.base / "untouched.json"
        outside.write_text("must survive")
        pending = self.proof.with_suffix(".pending.json")
        pending.symlink_to(outside)
        rc, out = self.run_tests()
        self.assertEqual(0, rc, out)
        self.assertEqual("must survive", outside.read_text())
        self.assertTrue(pending.is_symlink())
        self.assertTrue(self.proof.is_file())

    def test_ast_rejects_comments_calls_strings_and_wrong_class_members(self):
        index = evidence.java_index([self.root / "src/Thing.java"])
        for symbol in ["Thing#missing", "Thing#deleted", "Thing#other", "Decoy#work", "wrong.Thing#work"]:
            with self.subTest(symbol=symbol), self.assertRaises(ValueError):
                evidence.resolve_symbol(self.root, {"file": "src/Thing.java", "symbol": symbol}, index)
        self.assertEqual(("example.Thing$Nested", "run"), evidence.resolve_symbol(
            self.root, {"file": "src/Thing.java", "symbol": "Thing.Nested#run"}, index))
        self.assertEqual(("example.Thing", "overload"), evidence.resolve_symbol(
            self.root, {"file": "src/Thing.java", "symbol": "example.Thing#overload(String)"}, index))
        with self.assertRaises(ValueError):
            evidence.resolve_symbol(self.root, {"file": "src/Thing.java", "symbol": "Thing#overload"}, index)

    def test_missing_method_rejects_verified_binding(self):
        self.doc["bindings"][0]["implementation"][0]["symbol"] = "Thing#deleted"
        self.write_binding()
        rc, out = self.validate("--require-symbols")
        self.assertNotEqual(0, rc)
        self.assertIn("method symbol missing", out)

    def test_reports_must_contain_exact_declared_test_class_and_method(self):
        for cls, method in [("other.ThingTest", "works"), ("example.ThingTest", "notExecuted")]:
            rc, out = self.run_tests(f'<testsuite><testcase classname="{cls}" name="{method}"/></testsuite>')
            self.assertNotEqual(0, rc, out)
            self.assertFalse(self.proof.exists())

    def test_failed_error_skipped_and_flaky_tests_are_not_evidence(self):
        for tag in ["failure", "error", "skipped", "flakyFailure", "rerunError"]:
            rc, out = self.run_tests(f'<testsuite><testcase classname="example.ThingTest" name="works"><{tag}/></testcase></testsuite>')
            self.assertNotEqual(0, rc, out)
            self.assertFalse(self.proof.exists())

    def test_parameterized_invocations_must_all_pass(self):
        self.doc["bindings"][0]["tests"][0]["symbol"] = "ThingTest#parameterized"
        self.write_binding()
        good = '<testcase classname="example.ThingTest" name="parameterized(int)[1]"/>'
        bad = '<testcase classname="example.ThingTest" name="parameterized(int)[2]"><failure/></testcase>'
        rc, out = self.run_tests('<testsuite>' + good + bad + '</testsuite>')
        self.assertNotEqual(0, rc, out)
        rc, out = self.run_tests('<testsuite>' + good + good.replace('[1]', '[2]') + '</testsuite>')
        self.assertEqual(0, rc, out)

    def test_design_source_binding_and_report_drift_each_invalidate_evidence(self):
        for target in [self.design, self.root / "src/Thing.java", self.binding, self.report]:
            rc, out = self.run_tests()
            self.assertEqual(0, rc, out)
            original = target.read_text()
            if target == self.design:
                target.write_text(original.replace('name: x', 'name: renamed'))
            elif target == self.binding:
                changed = json.loads(original); changed['bindings'][0]['notes'] = 'changed binding'
                target.write_text(json.dumps(changed))
            else:
                target.write_text(original + '\n')
            rc, out = self.validate("--require-evidence")
            self.assertNotEqual(0, rc, out)
            target.write_text(original)

    def test_additional_dependency_inputs_are_bound(self):
        dep = self.base / 'framework.java'; dep.write_text('class Framework {}')
        rc, out = self.run_tests(extra=["--input", "../framework.java"])
        self.assertEqual(0, rc, out)
        dep.write_text('class Framework { void changed() {} }')
        rc, out = self.validate("--require-evidence")
        self.assertNotEqual(0, rc)
        self.assertIn('dependency input changed', out)

    def test_design_digest_ignores_yaml_formatting_but_preserves_values(self):
        before = evidence.design_digest(self.design)
        self.design.write_text('# comment\nkind: data\ndatas:\n- name: x\n  id: DATA-X\n  properties: {id: int}\n')
        self.assertEqual(before, evidence.design_digest(self.design))
        self.design.write_text(self.design.read_text().replace('id: int', 'id: long'))
        self.assertNotEqual(before, evidence.design_digest(self.design))

    def test_stale_reports_or_failed_command_cannot_reuse_old_success(self):
        rc, out = self.run_tests()
        self.assertEqual(0, rc, out)
        rc, out = self.run_tests(code="pass")
        self.assertNotEqual(0, rc)
        self.assertIn('stale test report', out)
        self.assertFalse(self.proof.exists())
        rc, out = self.run_tests(code="raise SystemExit(7)")
        self.assertNotEqual(0, rc)
        self.assertFalse(self.proof.exists())

    def test_changes_during_test_run_cannot_be_certified(self):
        rc, out = self.run_tests(code="from pathlib import Path; Path('src/Thing.java').write_text('class Changed {}')")
        self.assertNotEqual(0, rc)
        self.assertIn('changed during', out)
        self.assertFalse(self.proof.exists())

    def test_complete_with_planned_is_never_release_ready(self):
        self.doc['bindings'][0]['status'] = 'PLANNED'; self.write_binding()
        rc, out = self.validate('--require-complete')
        self.assertEqual(0, rc, out)
        self.assertFalse(json.loads(out)['releaseReady'])
        rc, out = self.validate('--require-ready')
        self.assertNotEqual(0, rc)
        self.assertIn('release readiness requires VERIFIED', out)

    def test_runtime_managed_verified_nodes_still_require_tests(self):
        item = self.doc['bindings'][0]
        item.pop('implementation'); item.pop('tests'); item['runtimeManaged'] = True; item['runtimeCapability'] = 'Runtime'
        self.write_binding()
        rc, out = self.validate('--require-tests')
        self.assertNotEqual(0, rc)
        self.assertIn('tests required', out)

    def test_non_java_or_unresolvable_symbols_fail_explicitly(self):
        self.doc['bindings'][0]['implementation'][0] = {'file': 'src/code.py', 'symbol': 'work'}
        (self.root / 'src/code.py').write_text('def work(): pass')
        self.write_binding()
        rc, out = self.validate('--require-symbols')
        self.assertNotEqual(0, rc)
        self.assertIn('supports Java only', out)

    def test_class_binding_rejects_partial_test_class_execution(self):
        (self.root / 'src/ThingTest.java').write_text('package example; class ThingTest { @Test void works() {} @Test void second() {} }')
        self.doc['bindings'][0]['tests'][0]['symbol'] = 'ThingTest'
        self.write_binding()
        rc, out = self.run_tests()
        self.assertNotEqual(0, rc, out)
        self.assertIn('ThingTest#second', out)
        rc, out = self.run_tests('<testsuite><testcase classname="example.ThingTest" name="works"/>'
                                '<testcase classname="example.ThingTest" name="second"/></testsuite>')
        self.assertEqual(0, rc, out)

    def test_empty_reports_cannot_be_certified(self):
        rc, out = self.run_tests('<testsuite tests="0"/>')
        self.assertNotEqual(0, rc)
        self.assertIn('no executed testcases', out)


if __name__ == '__main__':
    unittest.main()
