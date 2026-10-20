# Skill 使用与维护

[返回文档总览](README.md)

## 1. 两个 Skill 的职责

Skill 是交给开发 Agent 的任务契约，包含操作说明、规范、模板、脚本和测试。它不等同于 Java 代码生成器，也不是部署到生产环境的业务执行器。

| | [dec-design](../skills/dec-design/SKILL.md) | [dec-implementation](../skills/dec-implementation/SKILL.md) |
| --- | --- | --- |
| 输入 | 已确认需求、业务模型、流程或已有设计 | 已批准且通过校验的 canonical YAML、现有工程 |
| 主要职责 | 建模、设计校验、XML/HTML 生成、导入/同步 | 接入 Runtime、实现适配器、测试、维护 Binding |
| 主要输出 | canonical YAML、XML、HTML、设计缺口、交接 ID | 真实工程代码、测试、实现绑定及证据 |
| 不负责 | 编写业务实现代码 | 擅自改变业务设计、手写派生 XML |
| 可独立使用 | 可以 | 可以；按消费契约接入同版本已校验设计 |

本项目不要求借助通用研发流程 Skill 才能运行。本文只说明两个 DEC Skill；不会引入 `common-develop`。

## 2. dec-design 如何工作

1. 根据需求确定本次涉及的 kind 和直接依赖。
2. 阅读规范来源、输入边界、规范索引及所需分片，而非一次性加载全部规范。
3. 用正式脚本读取现有 YAML 或模板。
4. 保持稳定 ID，通过 JSON payload/patch 与 YAML I/O 脚本修改设计。
5. 校验整个相关设计目录，检查跨文件引用和 Information/Directory 语义。
6. 生成 XML 和 HTML；有框架时继续编译、执行验证。
7. 交接新增/变更的 Design ID、Runtime-managed 节点、需手写的 Action/适配器、测试场景及 `DESIGN_GAP`。

规范入口为 [dec-yaml-spec.md](../skills/dec-design/references/dec-yaml-spec.md)，模板在 [templates](../skills/dec-design/templates)。XML 映射与 HTML 可视化分别有自己的规范，不能仅凭字段名称猜测转换关系。

### 正式工具

| 脚本 | 用途 |
| --- | --- |
| `yaml_io.py read/check/write/merge` | 受控读取、语法检查、创建和更新 YAML |
| `validate_dec_yaml.py` | canonical 字段、引用、枚举和跨节点设计校验 |
| `yaml_to_xml.py` | 确定性生成兼容 XML |
| `xml_to_yaml.py` | 初次导入已有 XML |
| `sync_xml_to_yaml.py` | 外部 XML 变更合并到已有 YAML |
| `render_dec_graph.py` | 生成完整 HTML 站点 |
| `check_manifest.py` | 检查 Skill 包清单与实际文件 |

以上脚本位于 [dec-design/scripts](../skills/dec-design/scripts)。XML 同步操作以 [Skill 同步规范](../skills/dec-design/references/xml-yaml-sync.md) 为准；场景选择与风险判断见 [同步决策指南](xml-to-yaml-sync.md)。

### YAML I/O 示例

```sh
python3 skills/dec-design/scripts/yaml_io.py read demo/order-payment-yaml/rule-payment.yaml

# JSON payload/patch 由本次设计工作准备，下面是路径用法示意。
python3 skills/dec-design/scripts/yaml_io.py write /tmp/rule-new.yaml \
  --from-json /tmp/rule-new.json
python3 skills/dec-design/scripts/yaml_io.py merge /tmp/rule-new.yaml \
  --patch-json /tmp/rule-patch.json
```

这些 I/O 规则约束 Skill/Agent 操作。用户或外部工具编辑 XML 是受支持的输入场景，但回到本项目后仍应走同步脚本。不要用临时解析器、直接文本替换或全量反向转换覆盖已有 canonical 文件。

## 3. dec-implementation 如何工作

实现 Skill 先按 [消费契约](../skills/dec-implementation/references/dec-design-consumer-contract.md) 确认设计版本和范围，再按 [实现工作流](../skills/dec-implementation/references/implementation-workflow.md) 确定复用、修改或新增的位置。

典型工作包括：生成工程骨架；提供 ServiceImpl、Workflow 和模型转换；注册实际 DataSource/CustomAction/Listener；明确事务所有权；编写真实场景测试；把每个 Design ID 绑定到生成代码、手写符号或 Runtime 能力。

Runtime 已承载的业务事实不需要再生成一个 Java 判断方法。`runtimeManaged: true` 表示运行时执行该节点，但仍需要真实的验证场景，不能因此省略测试。

| 脚本 | 用途 |
| --- | --- |
| `yaml_io.py` | 读取 canonical/Binding，受控写入 Binding |
| `xml_io.py` | 检查/读取派生 XML，不生成 XML |
| `validate_binding.py` | 覆盖、状态、文件、Java 符号和证据校验 |
| `run_binding_verification.py` | 执行真正测试，生成与本次输入关联的证据 |
| `JavaSymbolIndex.java` | 用 Java Compiler AST 检查声明的类/方法 |

脚本位于 [dec-implementation/scripts](../skills/dec-implementation/scripts)。Binding 的状态、签名和证据格式见 [Binding 契约](../skills/dec-implementation/references/implementation-binding.md)。

## 4. 硬要求和默认工程约定

| 不能被工程配置改变的语义要求 | 可按项目调整的默认约定 |
| --- | --- |
| YAML 为设计权威源，稳定 Design ID 可追踪 | 默认的目录/包布局 |
| API 契约、枚举值、Information/Directory 含义保持一致 | Controller/Service 按 URL 首资源分组 |
| 不复制 Runtime 的业务条件或流程状态机 | Store 使用 Factory 或既有 DI 装配 |
| 明确事务、数据源、访问控制和失败语义 | 实现类的组织方式 |
| 真实符号、测试与当前版本证据 | 团队采用的开发管理工具 |

生成器当前提供独立 `--generation-config` 来覆盖 API 分组。示例配置是 [order-payment-generation.properties](../demo/order-payment-generation.properties)，当前 Demo 默认生成命令没有启用它。配置影响文件布局，不改变 URL、业务含义和方法契约；调整后仍需迁移手写实现、Binding 并刷新证据。

## 5. 修改 Skill 时怎样保持一致

先判断改动属于设计语法、转换、运行时能力、生成布局还是验证证据。新增字段时不能只在 `SKILL.md` 写一句“支持”：还要同步规范/schema、I/O 和 Validator、XML 正反转换与同步投影、HTML、Java AST/编译器/Runtime，以及相关示例和测试。

若只调整 Java 文件分组，应改生成器和独立配置，不改业务 YAML。若修复 HTML 展示，应改渲染器并重生成，不维护一份手工页面。若新增外部能力，需说明适配器和事务限制，不能把可解析等同于已完成真实集成。

修改 Skill 包中的文件后，应检查包清单，并运行相关测试。具体命令见 [开发与验证](development-and-verification.md)。
