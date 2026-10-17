---
name: dec-implementation
description: 根据已批准的 DEC canonical YAML 独立实现或修改真实工程代码，并维护 Design ID 到代码符号、Runtime capability、测试与验证的 Implementation Binding。支持 DataSource、Enum、Data/View、RuleView/API/System/Information/Business 等节点；保持 RuleView/Rule 多 DataSource、relEnum 与 API 校验契约一致，不依赖外部研发流程 Skill。
---

# dec-implementation

本 Skill 把 **已批准的 DEC canonical YAML** 落为真实工程实现，并证明代码与设计一致。它可直接独立使用，也可与 `dec-design` 组合使用。

## 0. 实现前必须读取

至少读取：

1. `references/dec-design-consumer-contract.md`；
2. `references/input-contract.md`；
3. 若项目提供同版本 DEC canonical YAML 规范，按本次节点读取对应 kind 分片；没有独立的 `dec-design` 安装或项目规范时，使用本包 `references/dec-design-consumer-contract.md` 与 `references/input-contract.md` 作为最小消费契约，只消费已通过同版本校验的 canonical YAML；无法确认的语义报告 `DESIGN_GAP`；
4. 通过 `scripts/yaml_io.py read <canonical-yaml>` 读取本次 Design IDs 对应 YAML；禁止直接打开/解析 YAML；
5. `references/implementation-workflow.md`；
6. `references/implementation-binding.md`。

按需追加：

- DataSource、手动适配器注册与按次选择实现：`references/datasource-implementation.md`；
- Enum / `relEnum`：`references/enum-implementation.md`；Enum `value` 固定为 Integer，API/Entity/serializer 不得改成字符串；
- API / RuleView.apiRef：`references/api-implementation.md`；
- 框架通用性、并发、乱序、集合关系和失败场景验证：`references/scenario-validation.md`；
- Runtime 初始化、classpath 设计加载、编译复用：`references/runtime-assembly.md`；
- RuleView / Action / Produce / Change、业务流程绑定的 Action 前后监听器：`references/rule-action-implementation.md`。

如果 `dec-design` Skill 未安装，本包仍可按上述最小消费契约独立实现已校验的 canonical YAML；YAML 内容必须通过 `scripts/yaml_io.py` 读取，不要求任何特定编排器、项目布局或研发流程 Skill。

## 0.5 YAML / XML 强制脚本 I/O

**所有 YAML/XML 文件的读取与写入必须通过本 Skill 的正式脚本完成。**

- canonical DEC YAML 与 Binding YAML 读取：`python3 scripts/yaml_io.py read <file-or-dir>`；
- YAML 新建/覆盖：`python3 scripts/yaml_io.py write <output.yaml> --from-json <payload.json|->`；
- YAML 更新：`python3 scripts/yaml_io.py merge <input.yaml> --patch-json <patch.json|->`；
- Binding 校验：`scripts/validate_binding.py`（其内部脚本读取属于允许 I/O）；
- 派生 XML 如需检查/读取：`python3 scripts/xml_io.py check|read <file-or-dir>`；
- 本 Skill **不写 DEC XML**。XML 重建属于设计/Converter 边界；若需要 XML 新版本，必须使用正式 YAML→XML converter，不得直接生成或编辑 XML。

禁止 Skill/Agent 使用 `cat`、文本编辑器、shell 重定向、临时 Python parser 或其他 ad-hoc 方式直接读取、创建、修改 `.yaml/.yml/.xml`。

## 1. 权威顺序

1. 已批准 canonical DEC YAML；
2. 与该 YAML 同 revision 的需求/业务说明（若提供）；
3. 已批准实现决策/实施范围（若提供）；
4. 当前代码与测试事实；
5. generated XML。

XML 是派生格式。YAML 与 XML 不一致时先从 YAML 重生 XML。

## 2. 语义要求与默认工程约定

### 2.1 语义硬要求

以下要求不能被生成配置或文件布局覆盖：

1. canonical YAML 是业务事实来源；不得为了 Java 布局修改业务设计，不添加未声明的业务规则。无法满足设计时报告 `DESIGN_GAP`。
2. 每个实现节点保持稳定 Design ID 追踪；生成类、字段、方法与参数保留 `Design ID + YAML#路径 + XML#路径`，索引和 Binding 指向真实符号。布局变化后更新绑定和测试证据。
3. API 的 URL、HTTP method、参数/响应/验证、Enum value、System 归属、Information/Directory/Action/Produce/Back 语义不能因分组变化而漂移。
4. 不在 Store、Workflow 或 Service 复制 Runtime 已承载的业务判断、路径规划和状态规则；runtime-managed 节点不制造空壳业务实现。
5. 事务所有权、作用域和共享状态明确且可验证。Runner 显式选择外部托管或自管模式；最终写回/校验在提交前完成，失败按事务契约传播。对象锁不能冒充数据库事务或分布式一致性。
6. 已声明的真实 DataSource 接入不能被静默内存 fallback 替代；不增加与业务事实重复的状态副本，不以通知表替代用户 Custom Action。
7. generated XML 由正式 converter 派生；CodeGraph、Memory 等只是辅助索引。只实现本次 scope；本版本仅消费 `business.directories`。
8. 完整覆盖、符号存在、测试证据有效与发布就绪分别验证；PLANNED 和未实现能力必须如实保留。

### 2.2 默认工程约定（可覆盖）

默认约定用于缺少项目规定时的起点，不是 DEC 业务语义。优先遵循用户明确要求、现有项目架构和显式技术配置；常规布局/装配调整可直接执行并记录依据，无需仅因偏离默认而请求额外批准。已被用户选定的项目约定仍适用于该项目，不因本节自动撤销。

| 默认 | 允许的替代与边界 |
|---|---|
| `REUSE → COMPATIBLE_EXTEND → MODIFY → CREATE` | 优先复用；已有技术约束可调整实现策略，说明原因，不改变设计事实。 |
| Factory 创建普通 Store | 可使用 Spring `@Bean`、`@Component` 或已有 DI 机制，只要依赖、作用域、线程安全和事务所有权明确；不要把可变请求状态放入共享单例。 |
| `<base-package>.<system>.<layer>` 分包 | 可沿用模块/业务能力/端口适配器布局；System 语义归属与 Design ID 绑定仍保留。本仓库生成器当前只提供 API 分组覆盖，不宣称支持任意包布局配置。 |
| 同 System 下按 URL 首资源归组 Controller/Service | 可按子资源或业务能力分组，通过独立生成配置按 API Design ID 覆盖；不为类布局改 URL 或 System。避免机械地每个方法一文件或整个应用一个类。详见 `references/api-implementation.md`。 |
| 复用 `DirectoryWorkflowRunner.advance(...)`、生成资源清单及 classpath 加载 | 可适配项目已有初始化/组合入口，但不得重复实现业务规则；编译结果和请求状态必须隔离，保留相同失败与事务语义。 |
| 直接使用 Store，删除仅转发 DAO 的 UnitOfWork | 有实际资源/暂存变更或项目接口兼容需求时可保留；不得以空 commit/completed 状态假装物理提交。 |

覆盖默认时，在工程说明或 Binding notes 中记录实际约定、技术配置位置和验证方式；生成配置位于 canonical 设计目录之外，纳入版本管理和测试证据输入。分组变更要迁移手写 Service 实现、更新 Binding，并检查旧 Bean/文件清理，不只重新生成 Java。

## 3. 节点实现责任

| 节点 | 默认处理 |
|---|---|
| DataSource | 配置；新 type 时实现统一 DataSource SPI/Adapter |
| Enum | 优先 Runtime/共享枚举；否则生成单一代码枚举/映射，保持 value/name 不漂移 |
| Data | Runtime model/mapping；Column `relEnum` 必须关联同一 Enum |
| View | Runtime business model/relation |
| API | endpoint/DTO/transport validation；`relEnum`、required/min/max/length/regex 与错误信息必须一致；业务组合条件由 RuleView/Information 承载 |
| RuleView / Rule | 优先 Runtime-managed；支持 RuleView 默认 DataSource + Rule override |
| System | ownership/capability boundary |
| Information | 优先 Runtime evaluator，不复制 Java 条件 |
| Business / Directory | 优先 Runtime orchestration |
| Action + `ruleRef` | 执行指定 System RuleView |
| Action 无 `ruleRef` | 实现 CustomAction 并统一注册；Workflow 注入注册表，副作用时序见 rule-action-implementation.md |
| Produce / Change / Back | 保持后置条件、物化和补偿语义 |

## 4. 独立输入 / 输出

最少输入：

- canonical DEC YAML；
- 本次 Design IDs 或变更范围；
- 当前项目代码；
- 可写范围。

推荐输入：generated XML、已有 Binding、真实 build/test 命令、代码索引。

输出：

- 真实代码变更；
- `implementation-binding.yaml`；
- 生成代码的 Design ID 来源注释与索引（项目使用代码生成器时）；
- 测试/Compiler/场景验证结果；
- `DESIGN_GAP` 或 Runtime capability gap；
- 设计符合性 Review 结论。

## 5. 工作流

### Step 1：验证设计输入

- canonical YAML 通过相应 schema/validator；
- XML 若存在必须来自同 revision YAML；
- 读取本次 Design ID 依赖切片。

### Step 2：Runtime / Code 决策

```text
Runtime 已实现该设计事实
  → runtimeManaged binding + 验证
否则
  → 找真实代码承载点
```

不得机械“一节点一方法”。

### Step 3：发现现有实现

查模块、类、方法、Adapter、DataSource SPI、枚举、注册表、测试与调用链；事务/异常/权限/副作用回到真实代码确认。

### Step 4：选择策略

`REUSE → COMPATIBLE_EXTEND → MODIFY → CREATE`。CREATE 必须说明前三者为何不适用。

### Step 5：建立 Binding 草稿

编码前以 `PLANNED` 记录 Design ID、策略、预期 symbol/test seam。`implementation-binding.yaml` 必须通过 `scripts/yaml_io.py write/merge` 创建或更新，禁止直接编辑 YAML。


### Step 6：最小实现

只实现 YAML 规定语义；不得增加隐藏状态/fallback/权限/条件。Custom Action / DataSource adapter 显式注册；Runtime-managed RuleView/Information 不复制业务判断。优先使用框架提供的 Directory Workflow Runner/Session Facade，避免在每个 Workflow 重复创建 InformationEngine、RuleViewRegistry、ActionRuntime 和 ExecutionContext。

业务事实去重（状态恢复、单步迁移、持久化回读与 DTO 校验）按 `references/implementation-workflow.md` 执行；Store/Workflow 不得保留与 Information/流程边平行的判断。

业务状态优先使用设计对应的 View/Entity；已有 DAO/Repository 必须进入实际读写链路。Runtime 所需动态 Map 应收敛在单独适配边界，不得成为应用层的主业务模型。

Workflow/Service 只协调设计声明的调用顺序和事务边界，不通过方法级 `synchronized` 保护请求。需要并发控制时，在 Session、Transaction、DAO/Repository 或 DataSource 层实现，并验证冲突、回滚和重试语义。

### Step 7：真实验证

有行为的 Design ID 至少一种真实验证：unit/integration/scenario/Compiler/architecture contract。

### Step 8：完成 Binding

Binding 更新仍通过 `yaml_io.py`，随后执行：

```sh
python3 scripts/validate_binding.py \
  --design path/to/dec-yaml \
  --binding implementation-binding.yaml
```

交付前用 `--require-complete --require-tests --require-symbols --project-root ...` 检查覆盖、测试声明及真实 Java 符号；runtimeManaged 的已实现节点同样需要测试。用 `scripts/run_binding_verification.py` 包装真实测试命令生成当前设计/源码/Binding/报告摘要证据，再以 `--require-evidence` 核对。具体命令、JDK 要求和边界见 `references/implementation-binding.md`。

`--require-complete` 只检查 ID 覆盖率，允许 `PLANNED`/`BLOCKED`。应用发布就绪检查使用 `--require-ready`（隐含完整覆盖、符号、当前测试证据检查，要求所有 Binding 为 `VERIFIED`），同时核对真实场景与注册表。生成代码能编译但 Service 尚未绑定 Runtime 时，Binding 保持 `PLANNED`。

Binding 指向真实 module/file/symbol/test 或明确 Runtime capability。

### Step 9：符合性 Review

检查少实现、多实现、平行规则、旧实现残留、Owner/DataSource 错误、枚举漂移、API validation 漂移、执行顺序/失败语义错误、Workflow/Service/Controller 方法级 `synchronized`、Binding drift、测试缺口。

## 6. DataSource

RuleView 与 Rule 的 DataSource 语义：

```text
effectiveDataSource(rule) =
  action.dataSource
  else rule.dataSource
  else ruleView.dataSource
  else business.dataSource
```

不同 Rule 可以分别访问 MySQL、Redis、MongoDB、第三方系统或微服务。实现层必须通过统一 DataSource abstraction/adapter 落地，不得因底层类型不同复制一套业务 Rule。

原框架扩展按 `datasource-implementation.md` 的 DataSource/DataConnection 等职责接入；dec-java-lite 使用 `RuleDataAccess` + `RuleDataAdapter` 及显式注册，不要求复制原始全局单例。

## 7. Enum / relEnum

- `kind: enum` 是公共枚举事实源；
- Data `<column rel-enum>`、API parameter/field `rel-enum` 必须引用同一个 Enum；
- 代码 Enum/常量/serializer/db converter 不得改变 YAML 的 `value/name`；
- API 绑定公共 Enum 时不要另写一份 inline allowed-values；
- 变更枚举值必须检查 API、Data column、持久化数据和兼容性影响。

详见 `references/enum-implementation.md`。

## 8. API / RuleView

- URL/method/source/type/required/default/min/max/length/pattern/regex/enum/relEnum/expression/response 与 YAML 一致；
- request-level expression 仅用于纯 transport 组合约束，在进入 RuleView 前执行；业务状态、库存、权限和状态转换必须由 RuleView/Information 承载，不能生成到 Request DTO 的 `@AssertTrue`；
- 生成 `@NotNull`、`@NotEmpty`、`@Min`、`@Max`、`@Size`、`@Pattern` 时必须带 `message`，优先使用 YAML validation.message，缺省时使用稳定默认消息；
- RuleView.apiRef 存在时 endpoint 路由到该 RuleView，不复制平行业务逻辑；
- `RuleView.code` 用于快速查找/注册索引，不能替代 Design ID 或 name；
- `ruleRef` 引用 RuleView name，不是 Rule name。

## 9. Implementation Binding

```yaml
version: dec-binding/v1
bindings:
  - designId: ACT-SMS-NOTIFY
    strategy: CREATE
    status: VERIFIED
    implementation:
      - module: payment-service
        file: src/main/java/com/acme/payment/SmsNotifyAction.java
        symbol: com.acme.payment.SmsNotifyAction#execute
    tests:
      - module: payment-service
        file: src/test/java/com/acme/payment/SmsNotifyActionTest.java
        symbol: com.acme.payment.SmsNotifyActionTest#execute
```

Runtime-managed：

```yaml
- designId: INFO-ORDER-PAYABLE
  strategy: REUSE
  status: VERIFIED
  runtimeManaged: true
  runtimeCapability: Information expression evaluation
```

Enum 也可以绑定：

```yaml
- designId: ENUM-ORDER-STATUS
  strategy: REUSE
  status: VERIFIED
  implementation:
    - module: order-domain
      file: src/main/java/com/acme/order/OrderStatus.java
      symbol: com.acme.order.OrderStatus
```

## 10. 完成条件

- scope 内 Design IDs 全部有明确处置；
- 无阻塞 DESIGN_GAP；
- code/runtime 行为与 canonical YAML 一致；
- Design ID 注释、YAML/XML 节点路径与 `DEC-DESIGN-INDEX.md` 可以相互跳转；
- Runtime 已覆盖的编排没有在应用 Workflow 中重复实现；
- DataSource 继承/覆盖正确；
- relEnum 与 Enum 值一致；
- API required/min/max/length/regex/enum/message 一致，且业务组合条件未泄漏到 DTO/Controller；
- Binding 校验通过；
- 真实 build/test/Compiler/scenario 已运行并记录；
- 所有 YAML/XML I/O 均由正式 Skill 脚本完成；
- generated XML 可从当前 YAML 重建。

## 11. 按需读取

- YAML 通用脚本 I/O：`scripts/yaml_io.py`
- XML 派生物只读检查：`scripts/xml_io.py`
- 设计消费契约：`references/dec-design-consumer-contract.md`
- 实现流程：`references/implementation-workflow.md`
- 按节点计划：`references/implementation-by-node.md`
- DataSource：`references/datasource-implementation.md`
- Enum：`references/enum-implementation.md`
- API：`references/api-implementation.md`
- Rule/Action/Produce：`references/rule-action-implementation.md`
- Binding：`references/implementation-binding.md`
- 符合性 Review：`references/conformance-review.md`

### Business / Action DataSource

实现流程默认与动作覆盖时，遵循 `references/datasource-implementation.md`：Action → Rule → RuleView → Business；普通和 Back Action 一致。设计声明逻辑名称，执行选择注册实现；生成投影和 HTML 应同时可追踪。不得把实现名称写进 canonical 设计，不得通过改写共享 Rule 或请求 payload 传递可信路由配置。

Rule 实现必须完整保留 cmd/process/error/customer；checkData/checkDataPattern 必须实际读源，纳入执行前路由校验。使用 dec-java-lite 时先按 `references/rule-action-implementation.md` 检查表达式和驱动支持范围，不把“类型可解析”当成“语义全部实现”。
