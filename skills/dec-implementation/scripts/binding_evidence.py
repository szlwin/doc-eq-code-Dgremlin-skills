"""Evidence and Java declaration helpers shared by the Binding validator and test runner."""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml
import jsonschema
from safe_xml import read_safe_xml_bytes

VERSION = "dec-binding-evidence/v1"
DESIGN_ALGORITHM = "dec-yaml-json-sha256/v1"
DESIGN_KINDS = {"api", "business", "config", "data", "enum", "rule", "systems", "view"}
EXCLUDED = {"target", "build", ".git", ".gradle", "node_modules", "__pycache__", ".pytest_cache", ".venv"}


def digest(value):
    raw = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    return "sha256:" + hashlib.sha256(raw.encode()).hexdigest()


def file_digest(path):
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def yaml_digest(path):
    return digest(yaml.safe_load(path.read_text(encoding="utf-8")))


def design_digest(path):
    base = path if path.is_dir() else path.parent
    files = sorted(p for p in path.rglob("*") if p.suffix.lower() in {".yaml", ".yml"}) if path.is_dir() else [path]
    documents = {}
    for file in files:
        doc = yaml.safe_load(file.read_text(encoding="utf-8"))
        if isinstance(doc, dict) and doc.get("kind") in DESIGN_KINDS:
            documents[file.relative_to(base).as_posix()] = doc
    if not documents:
        raise ValueError("no canonical DEC design documents found")
    return digest(documents)


def source_digest(root):
    """Hash all source/config inputs, excluding explicitly documented build/cache directories."""
    if not root.exists():
        raise ValueError(f"source input does not exist: {root}")
    if root.is_file():
        return file_digest(root)
    paths = sorted(p for p in root.rglob("*") if p.is_file()
                   and not any(part in EXCLUDED for part in p.relative_to(root).parts)
                   and p.name != ".DS_Store")
    return digest({p.relative_to(root).as_posix(): file_digest(p) for p in paths})


def inside(root, name):
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError(f"bound path escapes project root: {name}")
    if not path.is_file():
        raise ValueError(f"bound file does not exist: {name}")
    return path


def java_index(paths):
    if not paths:
        return []
    with tempfile.TemporaryDirectory(prefix="dec-symbols-") as tmp:
        listing = Path(tmp) / "sources.txt"
        listing.write_text("\n".join(str(p) for p in sorted(set(paths))), encoding="utf-8")
        proc = subprocess.run(["java", str(Path(__file__).with_name("JavaSymbolIndex.java")), str(listing)],
                              text=True, capture_output=True, timeout=120)
    if proc.returncode:
        raise ValueError("Java declaration parsing failed: " + proc.stderr.strip())
    return json.loads(proc.stdout)


def resolve_symbol(root, loc, index):
    if not loc.get("file") or not (loc.get("symbol") or loc.get("class")):
        raise ValueError("symbol verification requires file and symbol/class")
    path = inside(root, loc["file"])
    if path.suffix != ".java":
        raise ValueError(f"symbol verification currently supports Java only: {loc['file']}")
    symbol = loc.get("symbol") or loc["class"] + ("#" + loc["method"] if loc.get("method") else "")
    cls, separator, method = symbol.partition("#")
    matches = [item for item in index if Path(item["file"]) == path
               and cls.replace("$", ".") in {item["class"], item["localClass"]}]
    if len(matches) != 1:
        raise ValueError(f"class symbol missing or ambiguous: {symbol} in {loc['file']}")
    item = matches[0]
    if separator:
        requested = re.sub(r"\s+", "", method)
        candidates = [m for m in item["methods"] if (m == requested if "(" in requested else m.split("(")[0] == requested)]
        if len(candidates) != 1:
            raise ValueError(f"method symbol missing or overloaded: {symbol}; use Class#method(parameterTypes) for overloads")
    if loc.get("class") and loc.get("symbol"):
        cls_field = loc["class"].replace("$", ".")
        if not cls_field in {item["class"], item["localClass"]}:
            raise ValueError(f"class conflicts with symbol: {symbol}")
    if loc.get("method") and loc.get("symbol") and loc["method"] != method:
        raise ValueError(f"method conflicts with symbol: {symbol}")
    return item["binaryClass"], method.split("(")[0] if separator else None


def junit_cases(path):
    root = ET.fromstring(read_safe_xml_bytes(path))
    if root.tag not in {"testsuite", "testsuites"}:
        raise ValueError(f"not a JUnit report: {path}")
    cases = []
    for suite in root.iter("testsuite"):
        for case in suite.findall("testcase"):
            cls, name = case.get("classname"), case.get("name")
            if not cls or not name:
                raise ValueError(f"JUnit testcase requires classname/name: {path}")
            bad = any(case.find(tag) is not None for tag in ("failure", "error", "skipped", "flakyFailure", "flakyError", "rerunFailure", "rerunError"))
            cases.append({"class": cls, "name": name, "passed": not bad})
        # Never allow an aggregate failure to disappear because testcase details were omitted.
        if any(int(suite.get(key, "0")) > 0 for key in ("failures", "errors", "skipped")):
            raise ValueError(f"JUnit report contains failed/error/skipped tests: {path}")
    if not cases:
        raise ValueError(f"JUnit report has no executed testcases: {path}")
    return cases


def verify_evidence(path, design, binding, root):
    evidence = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads((Path(__file__).resolve().parents[1] / "schemas/test-evidence.schema.json").read_text())
    jsonschema.Draft202012Validator(schema).validate(evidence)
    if evidence.get("version") != VERSION or evidence.get("exitCode") != 0:
        raise ValueError("evidence must describe a successful instrumented test run")
    for key, actual in (("designDigest", design_digest(design)), ("bindingDigest", yaml_digest(binding)),
                        ("sourceDigest", source_digest(root))):
        if evidence.get(key) != actual:
            raise ValueError(f"stale evidence: {key} differs from current inputs")
    for entry in evidence.get("additionalInputs", []):
        if source_digest((root / entry["path"]).resolve()) != entry["digest"]:
            raise ValueError(f"stale evidence: dependency input changed: {entry['path']}")
    reports = evidence.get("reports")
    if not isinstance(reports, list) or not reports:
        raise ValueError("evidence has no JUnit reports")
    cases = []
    for report in reports:
        file = inside(root, report["file"])
        if file_digest(file) != report["digest"]:
            raise ValueError(f"test report changed after evidence was recorded: {report['file']}")
        cases.extend(junit_cases(file))
    return cases


def test_is_proven(symbol, cases, index):
    cls, method = symbol
    declaration = next(item for item in index if item["binaryClass"] == cls)
    expected = [method] if method else declaration["testMethods"]
    if not expected:
        raise ValueError(f"class-level test binding has no recognized JUnit test declarations: {cls}; bind explicit methods")
    for name in expected:
        if sum(m.split("(")[0] == name for m in declaration["methods"]) > 1:
            raise ValueError(f"overloaded test cannot be unambiguously matched to a report: {cls}#{name}")
        matching = [c for c in cases if c["class"] == cls and re.split(r"[([]", c["name"], maxsplit=1)[0] == name]
        if not matching or any(not case["passed"] for case in matching):
            raise ValueError(f"no fully passing executed test evidence for {cls}#{name}")
