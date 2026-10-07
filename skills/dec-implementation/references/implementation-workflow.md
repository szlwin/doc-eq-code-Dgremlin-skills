# DEC 实现工作流

## 1. 设计切片

不要一次把整个项目 YAML 和整个仓库塞给 AI。根据本次 Design IDs 取最小语义切片：

```text
Design ID
  → 所属 System/Business/P3 Business Directory/Rule
  → 直接依赖 Information/Data/View
  → 现有 Binding
  → 相关代码 symbol/callers/tests
```

## 2. 代码事实发现

优先级：

1. 已有 Implementation Binding；
2. CodeGraph/GitNexus/graphify 等 symbol/call graph；
3. 代码搜索；
4. 真实文件阅读；
5. 构建/测试结果。

代码图只负责快速定位。涉及事务、异常、权限、副作用、状态变化时必须读取真实实现。

## 3. 实现策略

每个需要变更的实现对象固定按：

```text
REUSE
  ↓ 不满足
COMPATIBLE_EXTEND
  ↓ 不满足
MODIFY
  ↓ 不满足
CREATE
```

选择 `CREATE` 时必须说明：为什么现有 symbol 无法复用、扩展或修改；新 symbol 的 Owner 是谁；公共业务规则如何保持单一事实源。

## 4. Runtime-managed 与 Custom Code

方案 1 仍可能有一部分 DEC 语义由现有 Runtime 执行。不要为了“一节点一方法”创建空壳代码。

- Runtime 可直接执行：Binding 标记 `runtimeManaged: true`；
- 需要项目代码：Binding `implementation` 指向真实 symbol；
- 尚未实现：`status: PLANNED` 或 `BLOCKED`，不得标记完成。

## 5. 修改原则

- 代码必须实现 YAML，而不是重新解释 YAML；
- 技术细节可由实现选择决定，但业务结果、条件、顺序、失败语义不能漂移；
- 新增隐藏状态、隐式 fallback、额外权限或静默吞错都属于设计偏差；
- 发现设计缺口，返回 `DESIGN_GAP`。
