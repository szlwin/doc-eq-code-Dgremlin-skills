# 完整示例说明

本目录提供一套可跨文件校验的订单支付 DEC canonical YAML：

```text
orm-config.yaml
  ├─ enum-common.yaml
  ├─ data-order.yaml
  ├─ view-order.yaml
  ├─ rule-user.yaml
  ├─ rule-order.yaml
  ├─ rule-payment.yaml
  ├─ api-order.yaml
  ├─ systems-order.yaml
  └─ business-order-payment.yaml
```

其中：

- Enum：OrderStatus / OrderType / PaymentStatus / PayResultCode；
- Data：user/order/orderDetail/pay/payDetail；每个 Data 通过必填 `system` 声明归属，Column 可通过 `relEnum` 绑定公共枚举；
- View：UserInfo、OrderInfo；每个 View 通过必填 `system` 声明归属，Property 可写 `desc`，HTML 中类型从 Data 解析、枚举从 Column.relEnum 解析；
- RuleView：包含唯一 `code`、`desc`、默认 `dataSource` 与 Rule 级 DataSource override；
- API：`order.submitOrder`，包含 URL、POST、path/body、required、数值 min/max、长度、regex、relEnum、跨字段 expression 与 response；
- System：user/order/payment/common；
- Information：atomic + composite；
- Business：带 `desc` 的 ordered → paying → PayResult，并按 success/error case 分类；
- Custom Action：`smsNotify`；
- Back：支付结果返回 paying 时执行 `resetPayResult`。

生成的 XML 位于 `generated-xml/`，只作为对照和 Runtime/Compiler compatibility artifact，不作为编辑源。

HTML 设计站点位于 `generated-html/`：`order-payment.html` 是 Directory 首页，列出 Business Directory Maps；详细图位于 `directory/order-payment.html`。所有 System 统一位于 `generated-html/system/` 下；例如 `system/user/`、`system/order/`、`system/payment/`、`system/common/`。每个 System 目录包含 `index.html`、`data.html`、`views.html`、`ruleviews.html`、`apis.html`、`enums.html`、`information.html`。System 首页按 Data、View → RuleView、Enum、API、Information 导航。根目录保留全局技术索引作为兼容/跨系统索引。所有 HTML 必须由 `render_dec_graph.py` 重新生成并覆盖，禁止手工维护。

示例 `OrderStatus` 明确定义 `1/2/3/4`，并通过 Data Column `relEnum` 与 Information `changeData` 对齐；validator 会校验 change 写入的具体值确实存在于 Enum。Business `success/error` 是 `role: case` 目标，因此不再重复声明 `dependencies`。

重新生成：

```sh
python3 ../scripts/validate_dec_yaml.py .
python3 ../scripts/yaml_to_xml.py . -o generated-xml
python3 ../scripts/render_dec_graph.py . -o generated-html/order-payment.html
```

反向迁移已有 XML：

```sh
python3 ../scripts/xml_to_yaml.py generated-xml -o /tmp/dec-imported-yaml
python3 ../scripts/validate_dec_yaml.py /tmp/dec-imported-yaml
```

反向转换仅用于迁移；完成后仍以 YAML 为唯一编辑源。
