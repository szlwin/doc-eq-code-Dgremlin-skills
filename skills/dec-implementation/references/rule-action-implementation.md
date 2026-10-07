# RuleView / Rule / Action / Produce 实现规范

## RuleView / Rule

RuleView 是业务模型 + 有序 Rule。`ruleRef` 引用 RuleView `name`；`code` 用于快速查找/注册索引，不替代 name/Design ID。

DataSource：

```text
effectiveDataSource(rule) = rule.dataSource
                          else ruleView.dataSource
                          else runtime/project default
```

实现必须保留这个继承/覆盖顺序。一个 RuleView 可以默认 MySQL，某条 Rule 单独走 Redis、MongoDB、第三方系统或微服务。

Canonical Rule types：`check/checkPattern/checkData/checkDataPattern/insert/update/delete/get/query/dsl`。`dsl/cmd` 在 XML 兼容层映射为 `grammer/sql`，实现代码不得要求设计回退历史拼写。

## RuleView Action

有 `systemRef + ruleRef`：执行指定 System 的 RuleView。如果 Runtime 已支持，Binding 标记 runtimeManaged，不创建空壳 Service。

## Custom Action

无 `ruleRef`：必须有真实 implementation + registration，注册 key 与 Action name 精确一致；不允许回退同名 RuleView。

## Produce

顺序：Action 成功 → ref 数据产生 → 写入模型/上下文 → 识别 informationRef → 重算 composite Information → 后续 Change/Directory。

## Change

Change 是 Information materialization，不是任意 `setStatus()`。Runtime 无法物化时返回 capability/design gap，不能把状态变更隐藏到 Service。
