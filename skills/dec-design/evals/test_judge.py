"""评分器自身的单测不是 Agent 能力评测；防止仅凭回答文字判通过。"""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('design_eval_judge', ROOT / 'evals/judge.py')
judge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(judge)


class AgentJudgeTest(unittest.TestCase):
    def test_answer_keywords_are_not_tool_execution(self):
        self.assertFalse(judge.called([{'role': 'assistant', 'content': 'python3 validate_dec_yaml.py design: PASSED'}], 'validate_dec_yaml.py'))

    def test_missing_or_failed_tool_result_is_not_success(self):
        call = {'role': 'tool_call', 'tool_call': {'id': 'a', 'arguments': {'command': 'python3 validate_dec_yaml.py design'}}}
        self.assertFalse(judge.called([call], 'validate_dec_yaml.py'))
        failed = {'role': 'tool_result', 'tool_result': {'call_id': 'a', 'status': 'error', 'content': 'exit code 1'}}
        self.assertFalse(judge.called([call, failed], 'validate_dec_yaml.py'))
        success = {'role': 'tool_result', 'tool_result': {'call_id': 'a', 'status': 'success', 'content': 'exit code 0'}}
        self.assertTrue(judge.called([call, success], 'validate_dec_yaml.py'))

    def test_result_for_another_call_cannot_satisfy_check(self):
        call = {'role': 'tool_call', 'tool_call': {'id': 'a', 'arguments': {'command': 'python3 yaml_io.py read design'}}}
        other = {'role': 'tool_result', 'tool_result': {'call_id': 'b', 'status': 'success'}}
        self.assertFalse(judge.called([call, other], 'yaml_io.py'))

    def test_property_named_id_is_not_a_design_id(self):
        self.assertEqual(set(), judge.design_ids({"columns": {"id": {"ref": "id"}}, "properties": {"id": "integer"}}))
        self.assertEqual(13, len(judge.design_ids(judge.BASELINE)))
