#!/usr/bin/env python3
"""在临时工程中生成、打包 XML、装配 Spring 并执行 MySQL 业务路径；需要 reactor 已 install。"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from prepare_semantic_variants import ROOT, run


def command(*args):
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)


def verify():
    if not os.environ.get("DEC_MYSQL_JDBC_URL"):
        raise SystemExit("必须设置专用测试库 DEC_MYSQL_JDBC_URL；不得连接生产数据库")
    jars = [p for p in (ROOT / "dec-java-lite/dec-lite-cli/target").glob("dec-lite-cli-*.jar")
            if not p.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar"))]
    if len(jars) != 1:
        raise SystemExit("需要唯一 CLI jar；先执行 reactor install")
    templates = Path(__file__).with_name("generated-smoke")
    reports = ROOT / "dec-java-lite/target/generated-project-smoke"
    reports.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="dec-generated-smoke-") as directory:
        root = Path(directory)
        design = root / "design"
        shutil.copytree(ROOT / "dec-java-lite/fixtures/expense-approval", design)
        output = root / "app"
        for mode in ("expense", "default-api", "regrouped-api"):
            if mode == "default-api":
                run("yaml_io.py", "write", design / "api-expense.yaml", "--from-json", "-", payload={
                    "version": "dec/v1", "kind": "api", "apis": [{"id": "API-APPROVE", "name": "approve",
                    "system": "expense", "url": "/expenses/{id}/approve", "method": "POST", "request": {"params": [
                    {"id": "PARAM-EXPENSE-ID", "name": "id", "in": "path", "type": "long", "required": True}]}}]})
            run("validate_dec_yaml.py", design)
            args = ["java", "-jar", jars[0], "generate", "--yaml", design, "--output", output,
                    "--base-package", "com.example.expenses"]
            group = "Approvals" if mode == "regrouped-api" else "Expenses"
            if mode == "regrouped-api":
                config = root / "group.properties"
                config.write_text("version=dec-generation/v1\napi.group.API-APPROVE=Approvals\n")
                args += ["--generation-config", config]
            command(*args, "--dry-run")
            command(*args)
            run("yaml_to_xml.py", design, "-o", output / "src/main/resources/dec/design", "--emit-id-comments")
            # 应用拥有的接线文件不在 manifest 中；分组迁移显式更新实现的接口。
            files = {
                "ExpenseApplicationService.java": "src/main/java/com/example/expenses/app",
                "GeneratedWiringTest.java": "src/test/java/com/example/expenses",
            }
            for name, package in files.items():
                text = (templates / (name + ".template")).read_text()
                text = text.replace("@INTERFACE@", "" if mode == "expense" else f"implements com.example.expenses.expense.service.{group}Service")
                text = text.replace("@RESOURCE_COUNT@", "5" if mode == "expense" else "6")
                text = text.replace("@CALL@", "service.approve(1L);" if mode == "expense" else
                                    'mvc.perform(post("/expenses/1/approve")).andExpect(status().isOk());')
                text = text.replace("@GROUP_ASSERT@", "" if mode == "expense" else
                                    f'assertEquals(1, context.getBeansOfType(com.example.expenses.expense.service.{group}Service.class).size());')
                if mode == "regrouped-api" and name == "GeneratedWiringTest.java":
                    text = text.replace("@Test void failedEligibility", "@Test void oldGroupDoesNotSurviveMigration() {\n"
                        '        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.example.expenses.expense.controller.ExpensesController"));\n'
                        '        assertFalse(context.containsBean("expensesController"));\n    }\n\n    @Test void failedEligibility')
                target = output / package / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)
            if mode == "regrouped-api":
                for layer, suffix in (("controller", "Controller"), ("service", "Service")):
                    assert not (output / f"src/main/java/com/example/expenses/expense/{layer}/Expenses{suffix}.java").exists()
            # clean 防止已删源文件留下旧 class/Bean；真实启动会检测重复端点与缺失依赖。
            command(ROOT / "dec-java-lite/mvnw", "-B", "-f", output / "pom.xml", "clean", "package")
            built = list((output / "target").glob("*.jar"))
            assert len(built) == 1, "应生成唯一应用 JAR"
            with zipfile.ZipFile(built[0]) as jar:
                prefix = "BOOT-INF/classes/dec/"
                manifest = jar.read(prefix + "design-files.txt").decode().splitlines()
                assert len(manifest) == (5 if mode == "expense" else 6)
                for name in manifest:
                    assert jar.read(prefix + name) == (output / "src/main/resources/dec" / name).read_bytes()
            shutil.copytree(output / "target/surefire-reports", reports / mode, dirs_exist_ok=True)


if __name__ == "__main__":
    verify()
