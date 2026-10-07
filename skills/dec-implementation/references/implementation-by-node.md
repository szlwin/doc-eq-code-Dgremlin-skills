# 按 DEC 节点制定实现计划

以 Design ID 为最小追踪单元，不以“改几个文件”为主结构。

| Design ID | 节点 | Runtime / Code | Strategy | Target | Tests |
|---|---|---|---|---|---|
| `ENUM-ORDER-STATUS` | Enum | Runtime/Code | REUSE | OrderStatus + converters | serialization/db |
| `API-ORDER-SUBMIT` | API | Code | MODIFY | endpoint + validation | web/integration |
| `RV-PAY` | RuleView | Runtime | REUSE | runtimeManaged + DataSource routing | compiler/scenario |
| `ACT-SMS-NOTIFY` | Custom Action | Code | CREATE | SmsNotifyAction | unit/integration |
| `PROD-PAYMENT-INFO` | Produce | Runtime/Adapter | MODIFY | output mapping | scenario |

计划顺序：Design IDs → 直接依赖（含 Enum/DataSource）→ Runtime/Code 判断 → `REUSE → COMPATIBLE_EXTEND → MODIFY → CREATE` → Binding 草稿 → 实现 → 测试 → Binding → conformance review。

禁止用一个宽泛 Service binding 替代全部设计节点。
