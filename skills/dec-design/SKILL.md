---
name: dec-design
description: 将已确认的软件需求、业务模型和业务流程转换为规范驱动的 DEC canonical YAML，并依据 DEC 文档规则做字段、引用与业务语义校验，再确定性生成兼容 DEC Runtime/Compiler 的 XML。适用于 DataSource、Enum、Data、View、RuleView、API、System、Information、BusinessScope/Directory/Action/Produce/Change/Back；支持 RuleView/Rule 多 DataSource、公共枚举绑定与 API required/min/max/length/regex/enum/expression 校验；不负责编写业务实现代码。
---

# dec-design

本 Skill 负责 **DEC 设计事实**。默认输出 YAML；XML 是由 YAML 生成的派生产物，不手工维护。

## 0. 开始写 YAML 前的强制读取

**禁止只凭本文件中的概要直接编写 DEC YAML。**

每次创建/修改 DEC YAML 时，按**按需读取**原则执行：

1. 首次进入任务读取 `references/source-basis.md`；
2. 读取 `references/input-and-source-boundaries.md`，先判断输入是 canonical、legacy、XML 还是 HTML recovery artifact；
3. 读取 `references/dec-yaml-spec.md` **索引**，确定当前 kind 所需分片；
4. 读取 `references/yaml-spec/common.md` + 当前 kind 对应分片，禁止无差别加载全部规范；
5. 通过 `scripts/yaml_io.py read templates/<kind>.yaml` 读取对应模板；禁止直接读取/复制 YAML 文件内容；
6. 同时修改多个 kind 或做跨文件设计时，再读 `references/yaml-spec/cross-file.md` 与 `references/validation-rules.md`；
7. 只有需要生成/检查 XML 时才读取 `references/xml-compatibility-spec.md` 索引、`references/xml-mapping/common.md` 和当前 kind 对应 XML 分片，再读 `references/yaml-to-xml.md`；
8. 只有在迁移/导入已有 XML 时读取 `references/xml-to-yaml.md`，使用 `scripts/xml_to_yaml.py`，转换后立即回到 canonical YAML 工作流；若是“用户/外部系统修改 XML 后同步回已有 YAML”，必须改读 `references/xml-yaml-sync.md` 并使用 `scripts/sync_xml_to_yaml.py`；
9. 需要 HTML 设计文档时，读取 `references/design-html-generation.md`；涉及 Directory / Information 图形语义时再读 `references/business-visualization.md`。所有 DEC HTML 必须由 `scripts/render_dec_graph.py` 生成，禁止手工创建/维护；YAML 修改后允许并要求重新生成覆盖旧 HTML。

本包的 `examples/` 仅用于验证和说明，不是运行时依赖；用户项目可以完全不携带它们。

### 规范来源限制

本包 `references/` 中的规范分片是独立运行所需的操作契约。它们的语义基线来自 DEC 文档中的以下主题：

概念与设计分离、架构/DataSource、Data/View/Rule、Directory、Information、数据源扩展和模块职责。

独立的数据声明语言不属于本 Skill 的 canonical DEC YAML。只有任务明确要求迁移该语言时才读取其专用规范，并保持与 `kind: data/view/rule/business` 分离；详见 `references/source-basis.md` 与 `references/input-and-source-boundaries.md`。

## 0.5 YAML / XML 强制脚本 I/O

**所有 YAML/XML 文件的读取与写入都必须通过本 Skill 提供的脚本完成。** 这是硬约束，不是建议。

- YAML 读取：`python3 scripts/yaml_io.py read <file-or-dir>`；
- YAML 语法检查：`python3 scripts/yaml_io.py check <file-or-dir>`；
- YAML 新建/覆盖：先准备 JSON payload，再使用 `python3 scripts/yaml_io.py write <output.yaml> --from-json <payload.json|->`；
- YAML 增量更新：先准备 JSON patch，再使用 `python3 scripts/yaml_io.py merge <input.yaml> --patch-json <patch.json|-> [-o output.yaml]`；
- canonical YAML 写入后必须运行 `scripts/validate_dec_yaml.py`；
- XML 读取/导入：只允许 `scripts/xml_to_yaml.py <xml> --stdout|--check`，需要持久化迁移时使用 `-o`；
- 外部 XML 变更同步：只允许 `scripts/sync_xml_to_yaml.py <changed.xml> --existing <canonical.yaml> ...`；该脚本做 projection/diff/merge/validation/rollback；
- XML 写入/覆盖：只允许 `scripts/yaml_to_xml.py <yaml> -o <xml>`；需要给用户/外部工具编辑的 XML 应增加 `--emit-id-comments`；
- Skill/Agent 自己仍禁止直接修改 generated XML；只有**用户或外部系统已经修改的 XML**可以作为 `sync_xml_to_yaml.py` 的受控输入。

禁止 Skill/Agent 使用 `cat`、`sed`、文本编辑器、shell 重定向、临时 Python/YAML/XML parser 或其他 ad-hoc 方式直接读取、创建、修改、覆盖 `.yaml/.yml/.xml`。脚本内部的文件访问属于允许实现。

读取 YAML 的目的如果只是 Design ID/语义校验，也必须调用正式脚本（例如 `validate_dec_yaml.py`）；不得绕过脚本自行解析。HTML 生成器可以内部读取 YAML，因为它本身就是受控 Skill 脚本。

## 1. 核心原则

1. **YAML 是唯一权威设计源**：AI 生成、修改和 Review 都针对 canonical YAML。
2. **XML 是派生产物**：只通过 `scripts/yaml_to_xml.py` 生成；不得维护 XML/YAML 双主。`scripts/xml_to_yaml.py` 用于历史/既有 XML 首次迁移；用户或外部系统修改 XML 后，必须使用 `scripts/sync_xml_to_yaml.py` 安全合并回 existing YAML，成功后 YAML 重新成为唯一权威源。
3. **设计与实现分离**：YAML 表达业务/架构语义，不嵌入 Java/Spring/MyBatis/Kafka SDK 类名等实现细节。
4. **Fail closed**：未知字段、无法解析引用、互斥语义冲突、当前 grammar 无法表达的能力必须失败或登记 `DESIGN_GAP`。
5. **稳定设计 ID**：需实现、Review、测试或追踪的节点应有稳定 `id`；ID 默认不输出为 XML 属性。
6. **精确引用**：大小写敏感；禁止 fuzzy match、同名猜测和静默 fallback。
7. **DataSource 是统一外部能力边界**：数据库、NoSQL、MQ、文件、第三方系统、HTTP/gRPC 微服务都可作为 DataSource；不另造平行 Service DSL。
8. **P3 ownership 优先**：Information 归属 System；BusinessScope 只通过 system-qualified InformationKey 编排。
9. **只保留 P3 Directory**：Directory 只能存在于 `business.directories`；旧顶层 `directories:` / `directory-config` 已移除。

## 2. Canonical YAML 八类文档

| kind | 顶层结构 | 主要语义 | 模板 |
|---|---|---|---|
| `config` | DataSource / Connection / `*Files` | 数据源实例、连接、文件装配 | `templates/config.yaml` |
| `enum` | `enums` | 公共业务枚举及值定义 | `templates/enum.yaml` |
| `data` | `datas` | 数据模型、外部数据映射、Column 枚举绑定 | `templates/data.yaml` |
| `view` | `views` | 业务模型、one-to-one / one-to-many | `templates/view.yaml` |
| `rule` | `ruleViews` | RuleView/Rule、code/desc、默认与特定 DataSource、可选 API | `templates/rule.yaml` |
| `api` | `apis` | URL、Method、required/min/max/length/regex/enum/expression、返回值 | `templates/api.yaml` |
| `systems` | `systems` | System ownership、Information、ModelAccess | `templates/systems.yaml` |
| `business` | `business.directories` | Directory/Dependency/Action/Produce/Change/Back | `templates/business.yaml` |

字段的必填/可选、类型、枚举、条件必填、互斥和引用目标以 `references/dec-yaml-spec.md` 路由到的 `references/yaml-spec/*.md` 分片为准，不得根据字段名自行发挥。

## 3. 独立使用边界

`dec-design` 是独立 Skill，不依赖任何研发流程、任务系统或其他 Skill。

- 输入可以是需求、业务模型、流程、现有 DEC YAML/XML、架构约束；
- 输出是 canonical DEC YAML、generated XML、`DESIGN_GAP`、验证结果和实现交接 Design IDs；
- 禁止创建或修改业务实现代码；
- 外部编排器如需组合，只能消费本 Skill 的公开产物，不能改变 YAML authority、validator 或 Converter 规则；
- 本 Skill 不读取、调用或要求安装任何外部研发流程 Skill。

## 4. 工作流

### Step 1：确定 kind 和设计切片

不要一次加载整个项目。先确定本次要修改：Config / Enum / Data / View / Rule / API / Systems / Business 中哪些事实，以及它们的直接依赖。

### Step 2：加载规范和模板

先读规范索引，再只加载当前 kind 分片与对应模板。若修改 Business 且新增/调整 Information，再追加读取 `yaml-spec/system-information.md`；若修改 Rule，只需确认目标 View 引用存在，只有同步修改 View 时才加载 `yaml-spec/view.md`。

### Step 3：按依赖顺序建模

推荐顺序：

```text
DataSource / Connection
    ↓
Enum
    ↓
Data
    ↓
View
    ↓
API + RuleView（可选绑定）
    ↓
System + Information + ModelAccess
    ↓
Business + Directory + Action + Produce + Change + Back
```

### Step 4：通过脚本编写 canonical YAML

禁止直接编辑 `.yaml/.yml`。先用 `yaml_io.py read` 读取已有文档/模板；新建时把目标结构准备为 JSON payload 后用 `yaml_io.py write`；修改时优先用 `yaml_io.py merge` 或完整 `write` 原子覆盖。

示例：

```sh
python3 scripts/yaml_io.py read templates/api.yaml
python3 scripts/yaml_io.py write design/api-order.yaml --from-json /tmp/api-order.json
python3 scripts/yaml_io.py merge design/api-order.yaml --patch-json /tmp/api-patch.json
python3 scripts/validate_dec_yaml.py design
```


- 使用 camelCase；
- 使用稳定 `id`；
- 多行 `expression/ruleData/changeData/process` 使用 `|`；
- Rule canonical 类型使用 `dsl`，不要新写历史 `grammer`；
- RuleView 必须有 `code`，推荐 `desc`；`dataSource` 是子 Rule 默认数据源，Rule 自身 `dataSource` 可覆盖；`apiRef` 可选；
- 公共枚举使用独立 `kind: enum`；每个枚举项必须有具体 `value`；Data Column 与 API 字段通过 `relEnum` 绑定，XML 输出 `rel-enum`；Information `changeData` 写入 enum-bound property 时，值必须存在于该 Enum；
- API 使用独立 `kind: api` 描述 URL、HTTP method、required、length、enum/relEnum、request-level expression 与 response；
- 数据源 command canonical 字段使用 `cmd`，不要新写历史 `sql`；
- 不引入主规范之外的字段。

### Step 5：静态校验

```sh
python3 scripts/validate_dec_yaml.py <file-or-dir>
```

目录级校验优于单文件校验，因为可以检查跨文件引用和 Information graph。

### Step 5.5：迁移已有 XML（仅迁移场景）

已有 DEC XML 需要纳入 canonical YAML 管理时：

```sh
python3 scripts/xml_to_yaml.py <xml-file-or-dir> -o <yaml-file-or-dir>
python3 scripts/validate_dec_yaml.py <yaml-file-or-dir>
```

默认 `--id-mode auto`：优先恢复 `<!-- dec-id: ... -->`，没有注释时生成确定性 Design ID。迁移后必须以 YAML 为 Source of Truth，不建立 XML/YAML 双主。详见 `references/xml-to-yaml.md`。

### Step 5.6：用户/外部系统修改 XML 后安全同步到已有 YAML

先预检查：

```sh
python3 scripts/sync_xml_to_yaml.py <changed.xml> --existing <canonical.yaml> --check
```

确认无冲突后：

```sh
python3 scripts/sync_xml_to_yaml.py <changed.xml> \
  --existing <canonical.yaml> \
  --in-place \
  --validate-root <canonical-yaml-dir> \
  --report <sync-report.json>
```

规则：只把 XML 可表达的变化合并回 YAML；保留 YAML-only metadata 与既有 Design ID；实体删除默认阻塞，必须显式 `--allow-delete`；无 `dec-id` 的歧义 rename 必须 fail closed。需要外部编辑的 XML 应由 `yaml_to_xml.py --emit-id-comments` 生成。同步成功后重新由 YAML 生成 XML，不形成双主。详见 `references/xml-yaml-sync.md`。

### Step 6：生成/检查 XML

只检查：

```sh
python3 scripts/yaml_to_xml.py <file-or-dir> --check
```

生成：

```sh
python3 scripts/yaml_to_xml.py <yaml-dir> -o <generated-xml-dir>
```

XML 格式必须遵守 `references/xml-compatibility-spec.md` 路由到的 `references/xml-mapping/*.md` 分片。Skill/Agent 生成后不得手工修补 XML。若 XML 将交给用户/外部工具修改，推荐 `--emit-id-comments`；外部变更回流只能走 `sync_xml_to_yaml.py`。

### Step 6.5：生成 HTML 设计文档（按需）

需要生成可阅读、可跳转的 DEC 设计文档时：

```sh
python3 scripts/render_dec_graph.py <yaml-file-or-dir> -o <business.html>
```

一次生成 Business Directory、按 System 拆分的 Information dependency、每个 System 的聚合设计页，以及 DataSource、Data、View、RuleView、API、System、Enum、Overview HTML。**View / RuleView / API / Enum / Information 必须以 System 作为首要人工导航入口**：System → View → RuleView，并在 System 内列出 Enum、API、Information；全局类型页仅作为稳定交叉链接/检索目标。所有引用应转换为相对跳转链接。Business / Information 图支持缩放、平移、节点拖动与连线跟随。Information 依赖箭头固定为“依赖者 → 被依赖者”；Business depend 固定为“Directory → prerequisite Information”，case 目标不得再声明 depend。

**所有 DEC HTML 必须由该脚本创建。禁止手工新建或编辑后继续维护；canonical YAML 有修改时，重新运行脚本并覆盖原 HTML。** HTML 永远只是派生文档。详见 `references/design-html-generation.md` 与 `references/business-visualization.md`.

### Step 7：Compiler / Runtime 验证

Converter 成功只表示 YAML 能确定性映射为 XML，不表示 Runtime 语义已经支持。项目存在 Compiler/Runtime test 时必须运行真实验证；未运行时明确标记 `compiler_validation: NOT_RUN`。

### Step 8：实现交接

列出：

- 新增/修改 Design IDs；
- Runtime-managed 节点；
- Custom Action / 新 DataSource 等需代码实现节点；
- `DESIGN_GAP`；
- 关键业务 scenario；
- canonical YAML 与 generated XML 路径。

## 5. 关键业务规则

### Information

`ruleRef`、`ruleData`、`expression` 三选一且只能一个：

```text
countNotEmpty(ruleRef, ruleData, expression) == 1
```

- RuleView 型原子 Information：`viewRef + ruleRef`；
- 数据表达式型原子 Information：`viewRef + ruleData`，可有 `changeData`；
- composite Information：只有 `expression`，不得有 `viewRef/changeData`；
- `ruleRef` 指向 RuleView name，不是内部 Rule name；
- Information graph 不得有环。

### API / RuleView / Enum / DataSource

- API 唯一键为 `<system>.<apiName>`；RuleView `apiRef` 可选且必须精确解析；
- RuleView `code` 必填且唯一，`desc` 用于快速理解；
- RuleView `dataSource` 是默认值，Rule `dataSource` 是特定覆盖；二者都引用 Config DataSource；
- DataSource 可以是 MySQL/Redis/MongoDB/第三方系统/微服务等统一外部能力；
- API 支持 required、数值 `min/max`、长度 `minLength/maxLength`、`regex`、inline enum、公共 `relEnum` 与 request-level `expression`；
- `relEnum` 必须解析到公共 Enum，且不得与同字段内联 enum values 双主；
- Path 参数必须与 URL `{name}` 一一对应并 `required: true`；
- API response `modelRef` 存在时必须引用 View；
- View `system` 必填并引用已定义 System，作为 View ownership 与 System-first HTML 导航依据。

### Directory / Action

- Directory 必须有 `informationRef` 和 `modelRef`；
- Dependency 只引用 Information；
- Action 有 `ruleRef` 时必须有 `systemRef`；
- Action 无 `ruleRef` 表示 Custom Action；
- RuleView Action 与 Custom Action 不允许互相 fallback；
- Action 顺序就是执行顺序。

### Produce / Change

- `produce.ref` 是实际数据/模型结果后置条件；
- 产出后续作为业务信息使用时必须有 `informationRef`；
- `produce.informationRef` 应指向可由该产出直接验证的原子 Information；
- Change 表示 Information 物化，不是随意写状态字段。

### Back

Back 绑定在明确 subDirectory relation 上，返回时逐级执行补偿，不允许跳过中间关系。

## 6. 当前兼容边界

- Data/View/Rule 的 XML 兼容历史 Runtime parser；
- canonical `type: dsl` 由 converter 输出历史 XML `type="grammer"`；
- canonical `cmd` 由 converter 输出 XML `sql`；
- Data XML 根使用 docs 标准 `<orm-data-mapping>`；现有 XML parser 只遍历 `<data>` 子节点，不依赖旧示例中的错误根拼写；
- Config 的 `apiFiles/enumFiles/systemFiles/businessFiles` 分别生成专用 file node；
- RuleView `code/desc/apiRef/dataSource` 映射到 `<rule-view-info>`；Rule `dataSource` 映射到 `<rule dataSource>`；
- Enum 输出 `<enum-config>`；Column/API `relEnum` 输出 `rel-enum`；
- `Business.desc → <business-config desc>`；
- 结构化 DataSource 的 Data grammar 尚未批准前，不擅自发明 `object/array/path` 字段。

详细边界见 `references/current-dec-compatibility.md`。

## 7. Ready 条件

只有同时满足：

- 范围内业务语义无未决歧义；
- 主规范必填/互斥/引用规则全部满足；
- Design ID 无重复；
- `validate_dec_yaml.py` 通过；
- `yaml_to_xml.py --check` 通过；
- canonical YAML 与 generated XML 均未被直接手工读写；所有 YAML/XML I/O 均可追溯到 Skill 脚本；
- generated XML 未被 Skill/Agent 手工编辑；若存在用户/外部系统 XML 变更，已通过 `sync_xml_to_yaml.py` 受控同步且无未决 conflict；
- 要求的 Compiler/Runtime 验证通过，或明确记录未运行及原因；
- 阻塞性 `DESIGN_GAP` 已关闭。

## 8. 按需读取索引

- **规范来源/排除范围**：`references/source-basis.md`
- **YAML 规范索引（先读）**：`references/dec-yaml-spec.md`
- **YAML 通用规则**：`references/yaml-spec/common.md`
- **YAML kind 分片**：`references/yaml-spec/config.md`、`enum.md`、`data.md`、`view.md`、`rule.md`、`api.md`、`system-information.md`、`business.md`
- **跨文件引用**：`references/yaml-spec/cross-file.md`
- 快速写法：`references/dec-yaml-authoring.md`
- **XML 映射索引**：`references/xml-compatibility-spec.md`
- **XML 映射分片**：`references/xml-mapping/*.md`
- YAML 通用脚本 I/O：`scripts/yaml_io.py`
- YAML → XML 转换器使用：`references/yaml-to-xml.md`
- XML → YAML 迁移工具使用：`references/xml-to-yaml.md`
- **外部 XML → existing YAML 安全同步**：`references/xml-yaml-sync.md`；脚本 `scripts/sync_xml_to_yaml.py`
- HTML 设计文档生成与跨页链接：`references/design-html-generation.md`
- Directory / Information Tree 图形语义：`references/business-visualization.md`
- 静态校验规则：`references/validation-rules.md`
- 设计 Review：`references/design-checklist.md`
- Runtime/Compiler 边界：`references/current-dec-compatibility.md`
- 可复制骨架：`templates/*.yaml`
- 完整业务范例：`examples/*.yaml` + `examples/generated-xml/*.xml` + `examples/generated-html/*.html`


### System-first HTML layout

- `Data.system` 与 `View.system` 是归属事实；System HTML 必须从脚本生成 Data / View→RuleView / Enum / API / Information 导航。
- 所有 System 必须统一输出到 `system/<system>/`；每个 System（含 common）目录内包含：`index.html`, `data.html`, `views.html`, `ruleviews.html`, `apis.html`, `enums.html`, `information.html`。禁止在 HTML 根目录直接创建 `<system>/`，以避免 System 名称与 `directory/`、`system/` 等保留目录冲突。
- Directory 首页必须列出 Business Directory Maps，详细图输出到 `directory/`。
- View Property `desc` 应用于业务说明；View HTML 的字段类型必须从 Data property 解析，Enum 必须从 Data Column `relEnum` 解析并链接，禁止手工复制不一致的 type/enum。
- 所有 HTML 仍必须由 `scripts/render_dec_graph.py` 创建或覆盖，禁止手工维护。
