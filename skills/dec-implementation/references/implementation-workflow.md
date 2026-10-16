# DEC 实现工作流

## 1. 设计切片

不要一次把整个项目 YAML 和整个仓库塞给 AI。根据本次 Design IDs 取最小语义切片：

```text
Design ID
  → 所属 System/Business/P3 Business Directory/Rule
  → canonical YAML 节点路径 / 派生 XML 节点路径
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

缺少项目特定约束时，默认按以下顺序选择实现策略；有明确兼容/架构理由时可调整并记录：

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

## 5.1 设计到代码的可读关联

生成器和手写实现都必须让读者沿着同一条链路核对：`Design ID → YAML 文件#节点路径 → XML 文件#节点路径 → Java 文件/符号 → 测试`。类、字段、方法和参数附近的注释使用稳定 Design ID 与路径；生成项目根目录的 `DEC-DESIGN-INDEX.md` 同时列出 YAML、派生 XML 和 Java 链接。XML 只是 YAML 的定位视图，不能成为第二个权威来源。

## 5.2 框架优先

如果 DEC Runtime 已提供 Directory、RuleView、Information、Session 或事务入口，应用 Workflow 只组装本次请求的 ModelContext、DataSource adapter、Custom Action 和错误映射。优先调用 `DirectoryWorkflowRunner.advance(...)`，由框架在显式事务模式下统一完成 Directory 执行、失败判断、模型回写和提交前完成回调（`CompletionHandler.complete`）；不得重复实现 Runtime 的业务判断和路径规则；项目既有装配入口可适配框架组件，但必须保持请求隔离与事务/失败语义。缺少可复用入口时应先登记 `Runtime capability gap`，再决定是否增加框架能力。

对已有实体，读取持久化 View 后用 `DirectoryWorkflowRunner.recognizeDirectory(model, adapter)` 恢复稳定节点；实体不存在由 Repository/DAO 判断，不能把“Information 无匹配”解释为“新实体”。识别排除瞬时 result 节点，稳定 Information 必须互斥且可判定；零匹配、多匹配、UNRESOLVED、ERROR 均拒绝，不能按优先级猜测。

对于一次只推进一步的命令 API，在 `advance` 前调用 `requireSingleStep(current, target, back)`：正向只允许 root 创建或一条 execution edge，Back 允许 case 所属链加一条显式 Back edge；拒绝重复目标和跳步。`advance/executeTo` 本身仍支持多步编排，不能直接删掉旧 guard 后调用它们改变 API 语义。Workflow 只指定 API 的目标节点，不手写允许的前置状态列表。

迁移旧代码校验时，区分三类事实：已有 Information/RuleView 直接复用；设计中缺失的既有业务约束先显式补入 canonical 或报告 DESIGN_GAP；唯一键、行锁、实体存在等技术条件留在存储边界。外部 provider 产生支付状态等返回值属于适配行为，不能演变为另一套流程判断。直接调用 Workflow 的请求体校验复用生成 DTO 的 Jakarta Validator，禁止再复制字段 min/max/length/regex。

验证应包括设计表达式/流程边变化后行为随设计改变、重复/跳步被拒绝、明细与支付状态不一致、持久化回读不满足 Information 时整体回滚。


## 5.3 默认布局与独立生成配置

默认按设计 `system` 使用 `<base-package>.<system>.<layer>` 分包，Controller/Service 按同 System 的业务资源归组。项目已有模块、子资源或业务能力边界可以覆盖此约定；必须保留 System 归属和 Design ID 到真实文件/方法的绑定。本仓库通过独立 properties 的 `api.group.<API-ID>` 覆盖生成分组，详见 `api-implementation.md`；不为 Java 布局修改 canonical URL、System 或业务规则。

布局变更后迁移手写 ServiceImpl、核对 manifest 清理与残留 Bean、更新 Binding 并重新采集证据。当前生成器支持分组覆盖，不支持任意包布局或 Store DI 模式自动配置。

## 6. 并发与事务边界

应用层的 Service、Workflow、Controller 方法不得使用 Java 方法级 `synchronized` 作为请求并发控制。方法级锁会把所有请求绑定到单个应用实例，掩盖真正的事务边界，也无法表达多实例部署下的冲突语义。

实现应把并发与一致性放在可验证的边界：DEC `ExecutionSession`、事务协调器、DAO/Repository、数据库约束或 DataSource 适配器。Workflow 直接使用 Store 的 DAO 访问器；只有 Unit of Work 实际拥有事务或待提交变更时才保留并显式提交它。Runtime 初始化、资源清单和编译复用遵循 `runtime-assembly.md`；Spring 托管 JDBC 时由 `@Transactional` 负责提交和回滚，Runtime 的持久化校验不承担提交职责，详见 `datasource-implementation.md`。Workflow 不应持有全局对象锁；需要串行化时，必须由事务/存储层提供并记录冲突、回滚和重试行为。

DataSource Store 默认通过 Factory 组装；项目可用 Spring @Bean/@Component 或已有 DI 机制，明确实际作用域和状态归属即可。不要把可变请求状态放入共享单例，也不要用对象锁冒充数据库事务。并发与一致性仍由明确的事务/存储边界承担，为冲突、回滚与重试保留测试 seam。

符合性 Review 检查实际构造/注入入口、作用域、共享状态和事务测试；不因缺少 Factory 或偏离默认包名直接判为语义不符合。
