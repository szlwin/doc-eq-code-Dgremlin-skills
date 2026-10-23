# Skill 使用与维护

[返回文档总览](README.md)

在这个仓库里，Skill 更像一份交接手册：它告诉 Agent 该读哪些规范、用什么脚本修改设计、怎样验证结果，以及哪些工作必须交给 Java 应用完成。Skill 不是 Java 生成器，也不是部署后替应用执行规则的服务。

## 1. 两个 Skill 各自负责什么

| | [dec-design](../../skills/dec-design/SKILL.md) | [dec-implementation](../../skills/dec-implementation/SKILL.md) |
| --- | --- | --- |
| 输入 | 已确认的需求、业务模型、流程或已有设计 | 已通过校验的 canonical YAML 和现有工程 |
| 主要工作 | 建模、设计校验、XML/HTML 生成、导入和同步 | 接入 Runtime、实现适配器、编写测试、维护 Binding |
| 主要输出 | YAML、XML、HTML、设计缺口和交接 ID | 工程代码、测试、实现绑定和证据 |
| 不负责 | 直接写业务实现代码 | 擅自改业务设计或手写派生 XML |

两个 Skill 可以分别使用。项目不要求另配通用研发 Skill 才能运行；本文只讲 DEC 相关的工作边界。

## 2. 修改一条设计通常要经过什么步骤

以订单支付里新增一个支付前检查为例，`dec-design` 一般按下面的顺序工作：

1. 找到涉及的 kind 和直接依赖，先读对应规范，而不是一次加载全部资料。
2. 用正式 YAML I/O 读取现有文件，保持已有 Design ID。
3. 通过 JSON payload/patch 修改设计，避免直接做文本替换。
4. 校验整个相关目录，特别是跨文件引用和 Information/Directory 语义。
5. 重新生成 XML 和 HTML；如果影响 Java 代码，再继续编译和运行验证。
6. 交接新增或变更的 Design ID、Runtime-managed 节点、需要手写的 Action/适配器、测试场景和 `DESIGN_GAP`。

规范入口是 [dec-yaml-spec.md](../../skills/dec-design/references/dec-yaml-spec.md)，模板在 [templates](../../skills/dec-design/templates)。XML 映射、同步和 HTML 展示都有独立规范，不能只看字段名称猜转换关系。

### 常用工具

| 脚本 | 适合做什么 |
| --- | --- |
| `yaml_io.py read/check/write/merge` | 受控读取、检查和更新 YAML |
| `validate_dec_yaml.py` | 检查字段、引用、枚举和跨节点语义 |
| `yaml_to_xml.py` | 确定性生成 XML |
| `xml_to_yaml.py` | 第一次从 XML 建立 YAML |
| `sync_xml_to_yaml.py` | 把外部 XML 的修改合并回已有 YAML |
| `render_dec_graph.py` | 生成完整 HTML 站点 |
| `check_manifest.py` | 检查 Skill 包清单是否与文件一致 |

脚本都在 [dec-design/scripts](../../skills/dec-design/scripts)。XML 同步的操作细节看 [Skill 同步规范](../../skills/dec-design/references/xml-yaml-sync.md)，何时适合同步、哪些情况要停下来人工判断，看[同步决策指南](xml-to-yaml-sync.md)。

例如：

```sh
python3 skills/dec-design/scripts/yaml_io.py read demo/order-payment-yaml/rule-payment.yaml

# JSON 文件由本次设计工作准备，下面只示意路径用法。
python3 skills/dec-design/scripts/yaml_io.py write /tmp/rule-new.yaml \
  --from-json /tmp/rule-new.json
python3 skills/dec-design/scripts/yaml_io.py merge /tmp/rule-new.yaml \
  --patch-json /tmp/rule-patch.json
```

用户或外部工具修改 XML 是支持的输入场景，但回到仓库后仍应经过同步脚本。不要用临时解析器、直接文本替换或全量反向转换覆盖 canonical YAML。

## 3. 实现 Skill 接手以后做什么

`dec-implementation` 先按[消费契约](../../skills/dec-implementation/references/dec-design-consumer-contract.md)确认设计版本和范围，再按[实现工作流](../../skills/dec-implementation/references/implementation-workflow.md)决定复用、修改或新增的位置。

在订单 Demo 中，这通常意味着：生成工程骨架，补上 `ServiceImpl`、`Workflow` 和模型转换，注册 DataSource、CustomAction、Listener，明确事务所有权，编写真正的场景测试，再把每个 Design ID 绑定到生成代码、手写符号或 Runtime 能力。

Runtime 已经执行的业务事实不需要再复制一个 Java 判断方法。`runtimeManaged: true` 只说明由 Runtime 负责执行，仍然需要真实测试。

| 脚本 | 用途 |
| --- | --- |
| `yaml_io.py` | 读取 canonical/Binding，受控写入 Binding |
| `xml_io.py` | 检查和读取派生 XML，不生成 XML |
| `validate_binding.py` | 检查覆盖、状态、文件、Java 符号和证据 |
| `run_binding_verification.py` | 执行测试并生成与当前输入关联的证据 |
| `JavaSymbolIndex.java` | 用 Java Compiler AST 检查类和方法声明 |

脚本位于 [dec-implementation/scripts](../../skills/dec-implementation/scripts)，Binding 的状态、签名和证据格式见[契约](../../skills/dec-implementation/references/implementation-binding.md)。

## 4. 哪些事情不能靠配置改变

| 设计语义 | 可以调整的工程习惯 |
| --- | --- |
| YAML 是权威源，Design ID 要稳定可追踪 | 目录和 Java 包怎么分 |
| API、枚举值、Information/Directory 含义保持一致 | Controller/Service 如何按资源分组 |
| 不复制 Runtime 的业务条件或流程状态机 | Store 使用 Factory 还是其他 DI 方式 |
| 事务、数据源、访问控制和失败语义要明确 | 实现类具体怎样组织 |
| 完成声明要有真实符号、测试和当前证据 | 团队使用什么开发管理工具 |

生成器支持独立的 `--generation-config` 来调整 API 分组。示例见 [order-payment-generation.properties](../../demo/order-payment-generation.properties)。它改变文件布局，不改变 URL、业务含义和方法契约；调整后仍要迁移手写实现、Binding 并刷新证据。

## 5. 修改 Skill 本身时，怎样避免只改了一半

先判断改动属于设计语法、转换、运行时能力、生成布局还是证据校验。新增一个字段时，通常要同时检查规范/schema、I/O、Validator、XML 正反转换和同步投影、HTML、Java 编译器/Runtime，以及示例和测试。

如果只是调整 Java 文件分组，就改生成器或独立配置，不要改业务 YAML；如果是 HTML 展示问题，就改渲染器并重生成整站，不维护一张手工页面；如果新增外部能力，要写清适配器和事务限制，不能把“能解析”写成“已完成集成”。

Skill 文件变动后要检查包清单并运行相关测试，命令见[开发与验证手册](development-and-verification.md)。
