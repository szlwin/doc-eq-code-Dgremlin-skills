#!/usr/bin/env python3
"""Agent 评测：检查产物、真实脚本调用与退出结果；无轨迹或缺少证据时失败。"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

SKILL = 'dec-implementation'
BASELINE = {'business-expense.yaml': {'version': 'dec/v1', 'kind': 'business', 'business': {'id': 'BUS-EXPENSE', 'name': 'expense-approval', 'directories': [{'id': 'DIR-EXPENSE-DRAFT', 'name': 'draft', 'informationRef': 'expense.draft', 'modelRef': 'ExpenseInfo', 'change': {'informationRef': 'expense.draft'}, 'isRoot': True}, {'id': 'DIR-EXPENSE-SUBMITTED', 'name': 'submitted', 'informationRef': 'expense.submitted', 'modelRef': 'ExpenseInfo', 'change': {'informationRef': 'expense.submitted'}, 'subDirectories': [{'rel': 'draft'}], 'actions': [{'id': 'ACT-EXPENSE-SUBMITTED', 'name': 'recordSubmitted', 'systemRef': 'expense'}]}, {'id': 'DIR-EXPENSE-APPROVED', 'name': 'approved', 'informationRef': 'expense.approved', 'modelRef': 'ExpenseInfo', 'change': {'informationRef': 'expense.approved'}, 'subDirectories': [{'rel': 'submitted'}], 'actions': [{'id': 'ACT-EXPENSE-APPROVED', 'name': 'recordApproved', 'systemRef': 'expense'}], 'dependencies': [{'informationRef': 'expense.eligible'}]}]}}, 'data-expense.yaml': {'version': 'dec/v1', 'kind': 'data', 'datas': [{'id': 'DATA-EXPENSE', 'name': 'expense', 'system': 'expense', 'properties': {'id': 'integer', 'status': 'integer', 'amount': 'decimal', 'reviewerEnabled': 'boolean'}, 'tables': [{'name': 'expense_request', 'dataSource': 'approvalDb', 'columns': {'id': {'ref': 'id'}, 'status': {'ref': 'status'}, 'amount': {'ref': 'amount'}, 'reviewerEnabled': {'ref': 'reviewerEnabled'}}, 'key': 'id', 'keyType': 'set'}]}]}, 'orm-config.yaml': {'version': 'dec/v1', 'kind': 'config', 'dataSources': [{'name': 'approvalDb', 'type': 'MySQL'}]}, 'systems-expense.yaml': {'version': 'dec/v1', 'kind': 'systems', 'systems': [{'id': 'SYS-EXPENSE', 'name': 'expense', 'dataRefs': ['expense'], 'viewRefs': ['ExpenseInfo'], 'information': [{'id': 'INFO-EXPENSE-DRAFT', 'name': 'draft', 'viewRef': 'ExpenseInfo', 'ruleData': 'status = 0', 'changeData': 'status : 0;'}, {'id': 'INFO-EXPENSE-SUBMITTED', 'name': 'submitted', 'viewRef': 'ExpenseInfo', 'ruleData': 'status = 1', 'changeData': 'status : 1;'}, {'id': 'INFO-EXPENSE-APPROVED', 'name': 'approved', 'viewRef': 'ExpenseInfo', 'ruleData': 'status = 2', 'changeData': 'status : 2;'}, {'id': 'INFO-EXPENSE-ELIGIBLE', 'name': 'eligible', 'viewRef': 'ExpenseInfo', 'ruleData': 'amount <= 1000 and reviewerEnabled = true'}]}]}, 'view-expense.yaml': {'version': 'dec/v1', 'kind': 'view', 'views': [{'id': 'VIEW-EXPENSE', 'name': 'ExpenseInfo', 'system': 'expense', 'targetMain': 'expense', 'properties': {'id': {'ref': 'id'}, 'status': {'ref': 'status'}, 'amount': {'ref': 'amount'}, 'reviewerEnabled': {'ref': 'reviewerEnabled'}}}]}}
CANONICAL_HASH = '0f0d0bd4b43f23c5989a15b6acfd0c29078997429b7b5ee7e16d5e483e9c54c0'


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def skill_root():
    for base in ('.codex/skills', '.agents/skills'):
        root = Path(base) / SKILL
        if (root / 'SKILL.md').is_file():
            return root
    raise AssertionError('当前工作区没有独立安装的目标 Skill')


def execute(script, *args, success=True):
    result = subprocess.run([sys.executable, str(skill_root() / 'scripts' / script), *map(str, args)],
                            text=True, capture_output=True)
    check((result.returncode == 0) == success, result.stdout + result.stderr)
    return result.stdout


def read(path):
    return json.loads(execute('yaml_io.py', 'read', path))


def documents(path):
    return {item['path']: item['document'] for item in read(path)['files']}


def calls(transcript):
    return [(m['tool_call'].get('id'), json.dumps(m['tool_call'].get('arguments', {}), ensure_ascii=False))
            for m in transcript if m.get('role') == 'tool_call' and m.get('tool_call')]


def called(transcript, script, extra='', expected_success=True):
    # 只接受工具调用中的执行参数；最终回答里写一个命令不算调用。
    for call_id, args in calls(transcript):
        if script not in args or extra not in args or not re.search(r'python[\d.]*\s', args):
            continue
        for message in transcript:
            result = message.get('tool_result', {})
            if message.get('role') != 'tool_result' or result.get('call_id') != call_id:
                continue
            text = json.dumps(result, ensure_ascii=False)
            ok = result.get('status') in ('success', 'completed') and not re.search(r'(exit_code|exit code)[\\" :]+[1-9]', text)
            if ok == expected_success:
                return True
    return False



def design_ids(value):
    if isinstance(value, dict):
        return ({value['id']} if isinstance(value.get('id'), str) and re.fullmatch(r'[A-Z][A-Z0-9-]+', value['id']) else set()).union(*(design_ids(v) for v in value.values()))
    if isinstance(value, list):
        return set().union(*(design_ids(v) for v in value))
    return set()


def grade():
    transcript = json.loads(Path(os.environ['EVAL_TRANSCRIPT_PATH']).read_text())
    check(bool(calls(transcript)), '缺少实际工具调用轨迹')
    check(called(transcript, 'yaml_io.py', 'read'), '没有通过正式 YAML I/O 读取设计/Skill 示例')
    case = json.loads(Path('case.json').read_text())['case']
    if case in ('incremental', 'chinese-design', 'standalone'):
        check(called(transcript, 'validate_dec_yaml.py'), '没有成功执行设计校验')
        check(called(transcript, 'yaml_to_xml.py'), '没有成功执行 XML 导出')
        execute('validate_dec_yaml.py', 'design')
        # 不信任 Agent 自述的“无漂移”；评分时独立重建并逐字节比较。
        with tempfile.TemporaryDirectory() as td:
            execute('yaml_to_xml.py', 'design', '-o', td, '--emit-id-comments')
            files = list(Path(td).glob('*.xml'))
            check(bool(files), '没有 XML 产物')
            for f in files:
                actual = Path('export') / f.name
                # 支持 Agent 合法选择无 ID 注释的导出；同一选项重新生成后比较。
                if not actual.exists() or actual.read_bytes() != f.read_bytes():
                    execute('yaml_to_xml.py', 'design', '-o', td)
                    check(actual.exists() and actual.read_bytes() == f.read_bytes(), '导出 XML 与当前 YAML 不一致')
        docs = documents('design')
        if case == 'incremental':
            expected = json.loads(json.dumps(BASELINE))
            for doc in expected.values():
                for system in doc.get('systems', []):
                    for info in system.get('information', []):
                        if info['name'] == 'eligible':
                            info['ruleData'] = 'amount <= 500 and reviewerEnabled = true'
            check(docs == expected, '增量变更修改了其他设计事实、ID 或文件')
        elif case == 'standalone':
            enums = [e for d in docs.values() for e in d.get('enums', [])]
            check(len(enums) == 1 and enums[0]['id'] == 'ENUM-EXPENSE-STATUS', '枚举身份错误')
            check([(e['value'], e['name']) for e in enums[0]['values']] == [(0,'draft'),(1,'submitted'),(2,'approved')], '枚举值/类型漂移')
        else:
            systems = [s for d in docs.values() for s in d.get('systems', [])]
            infos = {i['name']: i for s in systems for i in s.get('information', [])}
            check(set(infos) == {'draft','submitted','approved','eligible'}, 'Information 缺失或添加隐藏规则')
            check(re.sub(r'\s+', '', infos['eligible']['ruleData']) == 'amount<=1000andreviewerEnabled=true', '资格条件改变')
            dirs = [n for d in docs.values() for n in d.get('business', {}).get('directories', [])]
            check({n['name'] for n in dirs} == {'draft','submitted','approved'}, '目录不完整')
            byname = {n['name']:n for n in dirs}
            check(byname['draft'].get('isRoot') is True, '根目录错误')
            for name, parent, action in [('submitted','draft','recordSubmitted'),('approved','submitted','recordApproved')]:
                check([x['rel'] for x in byname[name]['subDirectories']] == [parent], '流程边错误')
                check([a['name'] for a in byname[name]['actions']] == [action], 'Custom Action 不一致')
            check(any(x.get('informationRef') == 'expense.eligible' for x in byname['approved']['dependencies']), '审批资格未接入流程')
    elif case == 'xml-ambiguity':
        check(hashlib.sha256(Path('canonical.yaml').read_bytes()).hexdigest() == CANONICAL_HASH, '歧义回流覆盖了权威设计')
        report = json.loads(Path('sync-report.json').read_text())
        check(report['status'] == 'CONFLICT' and any(x['code'] == 'AMBIGUOUS_RENAME_OR_REPLACEMENT' for x in report['conflicts']), '缺少真实冲突报告')
        check(any('sync_xml_to_yaml.py' in args for _, args in calls(transcript)), '未调用正式回流脚本')
    else:
        check(documents('design') == BASELINE, '实现任务篡改了 canonical 设计')
        if case == 'design-gap':
            gap = json.loads(Path('gap.json').read_text())
            check(gap.get('status') == 'DESIGN_GAP' and gap.get('designId') == 'ACT-REFUND' and gap.get('reason'), '未报告缺失设计')
            check(not list(Path('.').glob('src/**/*.java')), '凭空实现了退款业务')
        else:
            binding = read('implementation-binding.yaml')['bindings']
            required = {'ACT-EXPENSE-APPROVED'} if case == 'runtime-gap' else {'ACT-EXPENSE-APPROVED','ACT-EXPENSE-SUBMITTED'}
            check(required <= {b['designId'] for b in binding}, 'Binding 缺少本次要求的节点')
            check({b['designId'] for b in binding} <= design_ids(BASELINE), 'Binding 引用了不存在的设计节点')
            expected = 'BLOCKED' if case == 'runtime-gap' else 'PLANNED'
            check(all(b['status'] == expected for b in binding), '计划或能力缺口冒充 VERIFIED')
            check(called(transcript, 'validate_binding.py'), '未校验 Binding')
            execute('validate_binding.py', '--design', 'design', '--binding', 'implementation-binding.yaml')
            if case == 'runtime-gap':
                gap = json.loads(Path('capability-gap.json').read_text())
                check(gap.get('status') == 'BLOCKED' and gap.get('dataSource') == 'approvalDb' and gap.get('reason'), '没有能力缺口说明')
                check(not list(Path('.').glob('src/**/*.java')), '引入了未授权的驱动替身')
            else:
                check(any('validate_binding.py' in args and '--require-ready' in args for _, args in calls(transcript)), '未尝试就绪门禁')
                execute('validate_binding.py', '--design', 'design', '--binding', 'implementation-binding.yaml', '--project-root', '.', '--require-ready', success=False)
                check(not list(Path('.').glob('**/TEST-*.xml')), '本用例不应制造 JUnit 报告')
    print('PASS：产物结构、独立校验与实际调用轨迹符合用例契约')


if __name__ == '__main__':
    try:
        grade()
    except Exception as exc:
        print('FAIL：' + str(exc))
        sys.exit(1)
