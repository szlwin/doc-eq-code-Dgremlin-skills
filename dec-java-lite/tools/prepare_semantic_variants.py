#!/usr/bin/env python3
"""通过正式 YAML I/O、validator、converter 构建跨实现语义回归输入。"""
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "skills/dec-design/scripts"


def run(script, *args, payload=None):
    return subprocess.run([sys.executable, str(SCRIPTS / script), *map(str, args)],
                          input=json.dumps(payload, ensure_ascii=False) if payload is not None else None,
                          text=True, capture_output=True, check=True).stdout


def prepare(output):
    matrix = json.loads((Path(__file__).with_name("semantic-cases.json")).read_text())
    for case in matrix:
        target = output / case["name"]
        shutil.copytree(ROOT / "dec-java-lite/fixtures/mix", target / "yaml")
        design = json.loads(run("yaml_io.py", "read", target / "yaml"))
        changed = False
        for entry in design["files"]:
            doc = entry["document"]
            if doc.get("kind") != "systems":
                continue
            for system in doc["systems"]:
                if system["name"] == "payment":
                    for info in system.get("information", []):
                        if info["name"] == "success":
                            info.pop("ruleRef", None)
                            info.pop("expression", None)
                            info.pop("changeData", None)
                            info["viewRef"] = "OrderInfo"
                            info["ruleData"] = case["expression"]
                            changed = True
            run("yaml_io.py", "merge", target / "yaml" / entry["path"], "--patch-json", "-", payload=doc)
        assert changed, "fixture 的 payment.success 不存在"
        run("validate_dec_yaml.py", target / "yaml")
        run("yaml_to_xml.py", target / "yaml", "-o", target / "xml", "--emit-id-comments")
    return matrix


if __name__ == "__main__":
    prepare(Path(sys.argv[1]))
