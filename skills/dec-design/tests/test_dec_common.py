import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from dec_common import extract_info_refs, parse_change_assignments


class DecCommonTest(unittest.TestCase):
    def test_information_reference_is_not_extracted_from_longer_path(self):
        self.assertEqual({"order.payable", "payment.success"},
                         extract_info_refs("order.payable and payment.success"))
        self.assertEqual(set(), extract_info_refs("prefix.order.payable"))

    def test_change_literals_have_same_meaning_for_validator_and_html(self):
        self.assertCountEqual([("enabled", True), ("orderDetailList.active", False), ("status", 1)],
                              parse_change_assignments("enabled: true; every(orderDetailList, active: false); status: 1"))
