# 实现输入契约

`dec-implementation` 只消费已批准的 canonical DEC YAML 和真实工程代码。先对输入分层，避免把派生文档或旧配置当成实现规格。

| 输入 | 可否作为设计事实 | 用途 |
|---|---:|---|
| 带 `kind` 的 canonical DEC YAML | 是 | 读取 Design ID、依赖和实现约束 |
| 与 canonical YAML 同 revision 的 XML | 否 | Runtime/Compiler 排查；需要变更时回到 YAML |
| legacy runtime YAML 或独立声明 YAML | 否 | 迁移参考；先转换并重新批准 canonical YAML |
| HTML、Markdown、报告或 HTML recovery artifact | 否 | 人工核对和定位，不产生 Binding |

## 输入检查

1. 用 `scripts/yaml_io.py read` 读取设计和 Binding；不要直接解析 YAML。
2. 用设计侧 validator（若项目没有设计 skill，则使用同版本 validator）确认 `kind`、Design ID 和跨文件引用有效。
3. 看到 `artifact`、`sourceText`、`source_rows`、页面计数或导航字段时，停止并报告“派生展示工件不是设计输入”。
4. 看到没有 `kind` 的旧结构时，标记为 `LEGACY_INPUT`，不能直接创建 `implementation-binding.yaml`；先取得 canonical YAML 或登记 `DESIGN_GAP`。

实现绑定只引用 canonical YAML 中的稳定 Design ID。代码文件、symbol 和测试路径来自真实项目扫描结果，不从设计名称猜测。
