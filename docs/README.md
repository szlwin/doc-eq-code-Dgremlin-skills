# DEC 项目文档

本项目把业务设计组织成可校验、可生成、可执行、可追踪的 DEC 模型。`dec-design` 负责设计事实，`dec-java-lite` 负责解析、编译、执行和生成，`dec-implementation` 负责工程接入及实现证据，订单支付 Demo 把这些能力连接成实际应用。

本文档描述的是**当前 `doc-eq-code-Dgremlin-skills` 仓库**，不是相邻原 DEC 仓库的实现说明。文档中的命令除特别注明外，均从本仓库根目录执行。

## 文档导航

| 文档 | 主要内容 |
| --- | --- |
| [项目理念](philosophy.md) | “文档即编码”的含义、业务事实去重、设计与实现边界、可信度 |
| [整体架构](architecture.md) | 设计到运行的链路、模块分工、启动与请求生命周期、命令和查询 |
| [设计模型](design-model.md) | 八类 YAML、Data/View/RuleView、Information、Directory、Action、数据源 |
| [YAML、XML 与 HTML](yaml-xml-html.md) | 三种产物的职责、生成路径、HTML 阅读、运行时镜像、初次 XML 导入 |
| **[XML 修改后同步回 YAML](xml-to-yaml-sync.md)** | 同步方式选择、协作风险、典型事故与权威操作规范入口 |
| [Skill 使用与维护](skills.md) | 两个 Skill 的输入输出、规范分片、脚本、语义要求与工程约定 |
| [Java 框架详解](java-framework.md) | 编译与执行、DSL、事务、监听器、数据源、查询、生成器与扩展点 |
| [订单支付示例详解](order-payment-demo.md) | 设计到代码映射、六个 API、成功/失败分支、短信、查询示例及限制 |
| [开发与验证手册](development-and-verification.md) | 环境、生成、运行、测试、Binding 证据、变更检查与故障定位 |

历史检查与修复快照见 [历史记录](history/README.md)。

## 推荐阅读顺序

- **理解项目**：理念 → 整体架构 → 设计模型 → 示例。
- **编辑设计**：设计模型 → Skill → YAML/XML/HTML → 开发与验证。
- **已有 XML 改动要回写**：直接阅读 XML 同步指南；先保留外部改动，再运行转换，避免提前生成覆盖 XML。
- **接入 Java 应用**：整体架构 → Java 框架 → 示例 → 开发与验证。

## 仓库地图

```text
skills/
  dec-design/                 设计规范、模板、转换器、校验器、HTML 生成器
  dec-implementation/         实现契约、Binding、符号与测试证据校验
dec-java-lite/                Java 21 多模块框架及 CLI
demo/
  order-payment-yaml/         canonical 设计源
  order-payment-xml/          对外导出的 XML
  design-html/               多流程共享的设计文档站点
  order-payment-app/          生成代码 + 手写应用接入 + 测试
  order-payment-implementation-binding.yaml
  order-payment-generation.properties
docs/                        本组项目说明文档
```

运行时 XML 另打包在 Demo 的 `src/main/resources/dec/design/`。它与导出 XML 是同一设计的两个副本。Java 生成器维护 `dec/design-files.txt` 资源清单；转换器维护 XML 内容。

## 如何判断“设计已经实现”

实现完成需要与当前设计和源码相符的行为测试及 Binding 证据，覆盖完整也不等于发布就绪。原则见 [项目理念](philosophy.md#3-五条核心原则)，执行方法见 [开发与验证手册](development-and-verification.md#6-binding覆盖符号证据就绪)；订单 Demo 的设计规模、Binding 状态和未实现项集中见 [集成说明](../demo/INTEGRATION.md)。

这些文档解释实现与使用方式；精确字段和语法仍以 [设计规范索引](../skills/dec-design/references/dec-yaml-spec.md)、[实现消费契约](../skills/dec-implementation/references/dec-design-consumer-contract.md) 和框架源码为准。框架专题文档见 [dec-java-lite 文档入口](../dec-java-lite/README.md#文档)。
