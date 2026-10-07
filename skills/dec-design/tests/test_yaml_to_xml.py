import sys
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from yaml_to_xml import ConversionError, convert_document, load_yaml


class ConverterTest(unittest.TestCase):
    def test_examples_convert_and_are_well_formed(self):
        for path in sorted((ROOT / "examples").glob("*.yaml")):
            xml = convert_document(load_yaml(path))
            self.assertTrue(xml.startswith("<?xml"), path.name)
            self.assertGreater(len(xml), 40, path.name)
            ET.fromstring(xml)

    def test_data_system_and_view_property_desc_mapping(self):
        data_xml = convert_document(load_yaml(ROOT / "examples" / "data-order.yaml"))
        self.assertIn('<data name="order" system="order">', data_xml)
        view_xml = convert_document(load_yaml(ROOT / "examples" / "view-order.yaml"))
        self.assertIn('name="status" ref-property="orderStatus" desc="状态"', view_xml)
        self.assertIn('name="orderDetailList" relation="one-to-many"', view_xml)
        self.assertIn('desc="订单明细列表"', view_xml)

    def test_data_root_uses_docs_standard_name(self):
        xml = convert_document({
            "kind": "data",
            "datas": [{"name": "order", "system": "order", "properties": {"id": "int"}}],
        })
        self.assertIn("<orm-data-mapping>", xml)
        self.assertNotIn("<orm--data-mapping>", xml)

    def test_legacy_standalone_directory_rejected(self):
        with self.assertRaises(ConversionError):
            convert_document({"directories": [{"name": "legacy"}]})

    def test_legacy_directory_files_rejected(self):
        with self.assertRaises(ConversionError):
            convert_document({"directoryFiles": [{"path": "classpath:legacy/"}]})

    def test_business_root(self):
        xml = convert_document(load_yaml(ROOT / "examples" / "business-order-payment.yaml"))
        self.assertIn('<business-config name="order-payment" desc="订单创建、支付发起、支付结果处理的跨 System 业务流程">', xml)
        self.assertIn('information-ref="order.ordered"', xml)
        self.assertIn('rule-ref="save-Order"', xml)

    def test_system_root(self):
        xml = convert_document(load_yaml(ROOT / "examples" / "systems-order.yaml"))
        self.assertIn('<system name="order">', xml)
        self.assertIn('<information', xml)
        self.assertIn('<model-access model-ref="OrderInfo">', xml)

    def test_config_p3_file_nodes(self):
        xml = convert_document({
            "kind": "config",
            "systemFiles": [{"path": "classpath:dec/system/systems.xml"}],
            "businessFiles": [{"path": "classpath:dec/business/order.xml"}],
        })
        self.assertIn('<system-file path="classpath:dec/system/systems.xml"/>', xml)
        self.assertIn('<business-file path="classpath:dec/business/order.xml"/>', xml)
        self.assertNotIn("<orm-file path=\"classpath:dec/system", xml)
        self.assertNotIn("<orm-file path=\"classpath:dec/business", xml)


class ExtendedConverterTest(unittest.TestCase):
    def test_nested_view_relation(self):
        xml = convert_document({
            "kind": "view",
            "views": [{
                "name": "OrderInfo", "system": "order", "targetMain": "order",
                "properties": {
                    "id": "id",
                    "items": {
                        "relation": "one-to-many", "data": "orderDetail",
                        "key": "orderId", "relKey": "id",
                        "properties": {"id": "id", "skuId": "skuId"}
                    }
                }
            }]
        })
        self.assertIn('name="OrderInfo" system="order" target-main="order"', xml)
        self.assertIn('relation="one-to-many"', xml)
        self.assertIn('<property name="skuId" ref-property="skuId"/>', xml)

    def test_canonical_dsl_and_cmd_map_to_legacy_xml(self):
        xml = convert_document({
            "kind": "rule",
            "ruleViews": [{"name": "rv", "code": "RV", "viewRef": "V", "rules": [
                {"name": "dsl", "type": "dsl", "process": "status:1;"},
                {"name": "query", "type": "query", "property": "items", "cmd": "select * from items"},
            ]}]
        })
        self.assertIn('<rule name="dsl" type="grammer">', xml)
        self.assertIn('<customer-process><![CDATA[status:1;]]></customer-process>', xml)
        self.assertIn('name="query" type="query" property="items" sql="select * from items"', xml)

    def test_conflicting_cmd_and_sql_rejected(self):
        with self.assertRaises(ConversionError):
            convert_document({
                "kind": "rule",
                "ruleViews": [{"name": "rv", "code": "RV", "viewRef": "V", "rules": [
                    {"name": "q", "type": "query", "property": "items", "cmd": "a", "sql": "b"}
                ]}]
            })

    def test_rule_view_desc_and_api_binding(self):
        xml = convert_document({
            "kind": "rule",
            "ruleViews": [{
                "name": "submit", "code": "SUBMIT_ORDER", "desc": "提交订单", "viewRef": "OrderInfo", "apiRef": "order.submitOrder",
                "rules": [{"name": "insert", "type": "insert", "property": "OrderInfo"}],
            }],
        })
        self.assertIn('code="SUBMIT_ORDER"', xml)
        self.assertIn('desc="提交订单"', xml)
        self.assertIn('api-ref="order.submitOrder"', xml)

    def test_api_contract_mapping(self):
        xml = convert_document({
            "kind": "api",
            "apis": [{
                "name": "submitOrder", "desc": "提交订单", "system": "order",
                "url": "/orders/{orderId}/submit", "method": "POST",
                "request": {"params": [{
                    "name": "orderId", "in": "path", "type": "long", "required": True,
                    "validations": [{"type": "notNull"}, {"type": "min", "value": 1}],
                }]},
                "response": {"type": "object", "modelRef": "OrderInfo", "fields": [{"name": "id", "type": "long", "required": True}]},
            }],
        })
        self.assertIn('<api-config>', xml)
        self.assertIn('name="submitOrder" desc="提交订单" system="order" url="/orders/{orderId}/submit" method="POST"', xml)
        self.assertIn('<parameter name="orderId" in="path" type="long" required="true">', xml)
        self.assertIn('<validation type="min" value="1"/>', xml)
        self.assertIn('<response type="object" model-ref="OrderInfo">', xml)

    def test_config_api_file_node(self):
        xml = convert_document({"kind": "config", "apiFiles": [{"path": "classpath:dec/api/"}]})
        self.assertIn('<api-file-info>', xml)
        self.assertIn('<api-file path="classpath:dec/api/"/>', xml)

    def test_rule_view_default_and_rule_override_datasource(self):
        xml = convert_document({
            "kind": "rule",
            "ruleViews": [{
                "name": "save", "code": "SAVE", "desc": "保存", "viewRef": "OrderInfo", "dataSource": "mysql1",
                "rules": [
                    {"name": "insert", "type": "insert", "property": "OrderInfo"},
                    {"name": "cache", "type": "get", "property": "OrderInfo", "dataSource": "redis1"},
                ],
            }],
        })
        self.assertIn('dataSource="mysql1"', xml)
        self.assertIn('<rule name="cache" type="get" property="OrderInfo" dataSource="redis1"/>', xml)

    def test_enum_and_rel_enum_mapping(self):
        enum_xml = convert_document({
            "kind": "enum",
            "enums": [{
                "name": "OrderStatus", "desc": "订单状态",
                "values": [{"value": 1, "name": "OPEN", "desc": "打开"}],
            }],
        })
        self.assertIn('<enum-config>', enum_xml)
        self.assertIn('<enum name="OrderStatus" desc="订单状态">', enum_xml)
        self.assertIn('<enum-value value="1" name="OPEN" desc="打开"/>', enum_xml)

        data_xml = convert_document({
            "kind": "data",
            "datas": [{
                "name": "order", "system": "order", "properties": {"status": "int"},
                "tables": [{
                    "name": "orders", "dataSource": "mysql1", "key": "id", "keyType": "set",
                    "columns": {"status": {"ref": "status", "relEnum": "OrderStatus"}},
                }],
            }],
        })
        self.assertIn('rel-enum="OrderStatus"', data_xml)

    def test_api_expression_and_rel_enum_mapping(self):
        xml = convert_document({
            "kind": "api",
            "apis": [{
                "name": "submit", "system": "order", "url": "/orders", "method": "POST",
                "request": {
                    "params": [
                        {"name": "status", "in": "body", "type": "int", "required": True, "relEnum": "OrderStatus"},
                        {"name": "type", "in": "body", "type": "int", "required": True, "relEnum": "OrderType"},
                    ],
                    "validations": [{"type": "expression", "expression": "status = 1 and type = 1", "message": "组合无效"}],
                },
                "response": {"type": "object", "fields": [{"name": "status", "type": "int", "relEnum": "OrderStatus"}]},
            }],
        })
        self.assertIn('rel-enum="OrderStatus"', xml)
        self.assertIn('type="expression" expression="status = 1 and type = 1" message="组合无效"', xml)

    def test_config_enum_file_node(self):
        xml = convert_document({"kind": "config", "enumFiles": [{"path": "classpath:dec/enum/"}]})
        self.assertIn('<enum-file-info>', xml)
        self.assertIn('<enum-file path="classpath:dec/enum/"/>', xml)

    def test_business_result_back(self):
        xml = convert_document({
            "kind": "business",
            "business": {"name": "b", "directories": [{
                "name": "PayResult", "type": "result", "informationRef": "payment.hasResult",
                "modelRef": "OrderInfo", "isRoot": True,
                "subDirectories": [{
                    "rel": "paying", "back": {"name": "returnPaying", "actions": [
                        {"name": "resetPayResult", "systemRef": "payment", "ruleRef": "resetPayResult"}
                    ]}
                }]
            }]}
        })
        self.assertIn('<back name="returnPaying">', xml)
        self.assertIn('rule-ref="resetPayResult"', xml)


if __name__ == "__main__":
    unittest.main()
