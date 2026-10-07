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
3. 当前项目/同版本 DEC canonical YAML 规范索引与本次节点对应 kind 分片；
4. 通过 `scripts/yaml_io.py read <canonical-yaml>` 读取本次 Design IDs 对应 YAML；禁止直接打开/解析 YAML；
5. `references/implementation-workflow.md`；
6. `references/implementation-binding.md`。

按需追加：

- DataSource：`references/datasource-implementation.md`；
- Enum / `relEnum`：`references/enum-implementation.md`；
- API / RuleView.apiRef：`references/api-implementation.md`；
- RuleView / Action / Produce / Change：`references/rule-action-implementation.md`。

如果 `dec-design` Skill 未安装，也可以独立消费项目中的同版本 canonical YAML/spec；本包只要求输入满足 `input-contract.md`，YAML 内容仍必须通过 `scripts/yaml_io.py` 读取，不要求任何特定编排器、项目布局或研发流程 Skill。

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

## 2. 硬边界

1. 不得改设计迁就代码；无法实现时返回 `DESIGN_GAP`。
2. 不得把 YAML 未声明的业务规则藏入代码。
3. 每个代码承载的设计节点必须可追踪到稳定 Design ID。
4. 实现策略固定：`REUSE → COMPATIBLE_EXTEND → MODIFY → CREATE`。
5. Runtime-managed 节点不制造空壳代码。
6. CodeGraph/GitNexus/graphify 只是定位索引，不是设计权威。
7. Memory 不是项目事实权威。
8. 只修改本次 scope；未提供 scope 时由用户指定 Design IDs/文件范围决定。
9. 旧独立 Directory 不实现，只支持 `business.directories`。
10. generated XML 不手改。

## 3. 节点实现责任

| 节点 | 默认处理 |
|---|---|
| DataSource | 配置；新 type 时实现统一 DataSource SPI/Adapter |
| Enum | 优先 Runtime/共享枚举；否则生成单一代码枚举/映射，保持 value/name 不漂移 |
| Data | Runtime model/mapping；Column `relEnum` 必须关联同一 Enum |
| View | Runtime business model/relation |
| API | endpoint/DTO/transport validation；`relEnum`、required/min/max/length/regex/expression 必须一致 |
| RuleView / Rule | 优先 Runtime-managed；支持 RuleView 默认 DataSource + Rule override |
| System | ownership/capability boundary |
| Information | 优先 Runtime evaluator，不复制 Java 条件 |
| Business / Directory | 优先 Runtime orchestration |
| Action + `ruleRef` | 执行指定 System RuleView |
| Action 无 `ruleRef` | Custom Action，必须实现并注册 |
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

只实现 YAML 规定语义；不得增加隐藏状态/fallback/权限/条件。Custom Action / DataSource adapter 显式注册；Runtime-managed RuleView/Information 不复制业务判断。

### Step 7：真实验证

有行为的 Design ID 至少一种真实验证：unit/integration/scenario/Compiler/architecture contract。

### Step 8：完成 Binding

Binding 更新仍通过 `yaml_io.py`，随后执行：

```sh
python3 scripts/validate_binding.py \
  --design path/to/dec-yaml \
  --binding implementation-binding.yaml
```

交付前使用 `--require-complete` 确认 scope 内每个 Design ID 都有处置；需要行为测试的实现再加 `--require-tests`，并按项目实际根目录加 `--project-root` 检查绑定文件存在。

`--require-complete` 只检查 ID 覆盖率，允许 `PLANNED`/`BLOCKED`。应用发布就绪检查使用 `--require-ready`（隐含完整覆盖，要求所有 Binding 为 `VERIFIED`），同时核对真实场景与注册表。生成代码能编译但 Service 尚未绑定 Runtime 时，Binding 保持 `PLANNED`。

Binding 指向真实 module/file/symbol/test 或明确 Runtime capability。

### Step 9：符合性 Review

检查少实现、多实现、平行规则、旧实现残留、Owner/DataSource 错误、枚举漂移、API validation 漂移、执行顺序/失败语义错误、Binding drift、测试缺口。

## 6. DataSource

RuleView 与 Rule 的 DataSource 语义：

```text
effectiveDataSource(rule) =
  rule.dataSource
  else ruleView.dataSource
  else runtime/project default
```

不同 Rule 可以分别访问 MySQL、Redis、MongoDB、第三方系统或微服务。实现层必须通过统一 DataSource abstraction/adapter 落地，不得因底层类型不同复制一套业务 Rule。

新增 DataSource type 时按 `datasource-implementation.md` 实现 `DataSource/DataConnection/ConvertContainer/DataConvertContainer/ExecuteContainer` 及必要 factory/registration。

## 7. Enum / relEnum

- `kind: enum` 是公共枚举事实源；
- Data `<column rel-enum>`、API parameter/field `rel-enum` 必须引用同一个 Enum；
- 代码 Enum/常量/serializer/db converter 不得改变 YAML 的 `value/name`；
- API 绑定公共 Enum 时不要另写一份 inline allowed-values；
- 变更枚举值必须检查 API、Data column、持久化数据和兼容性影响。

详见 `references/enum-implementation.md`。

## 8. API / RuleView

- URL/method/source/type/required/default/min/max/length/pattern/regex/enum/relEnum/expression/response 与 YAML 一致；
- request-level expression 在进入 RuleView 前执行，但不替代业务 Rule；
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
- DataSource 继承/覆盖正确；
- relEnum 与 Enum 值一致；
- API required/min/max/length/regex/enum/expression 一致；
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
