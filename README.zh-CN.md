# DEC 设计与实现工具

[English](README.md) · 简体中文（当前）

## 这是什么项目

DEC 把业务系统里的数据、判断条件和流程写进一份结构化设计（canonical YAML），让同一份设计源同时驱动四件事：

- **校验**：静态检查字段、引用与跨节点语义，设计错误在生成代码之前暴露；
- **生成**：确定性派生 XML（对外交换、运行时加载）、HTML 阅读站点和 Java 工程骨架；
- **执行**：`dec-java-lite` Runtime 按设计编译并执行规则与流程，业务条件不再散落在各个接口里各写一份；
- **追踪**：Implementation Binding 记录每个设计节点由什么代码、测试和运行时能力负责，并关联测试证据，防止"声明先行于实现"。

仓库里的订单支付 Demo 把这条链路完整走通：从 10 份 canonical YAML 出发，生成 XML/HTML 与 Spring Boot/MyBatis 工程骨架，接上手写适配后在真实 MySQL 上跑通提交、支付、成功/失败分支、回退与业务阶段查询。

## 核心概念

| 概念 | 一句话说明 |
| --- | --- |
| canonical YAML | 唯一权威设计源；XML、HTML 和生成代码都由它派生，不反向维护 |
| Information | 表达"订单可支付"这类当前是否成立的业务事实，可被流程与查询复用 |
| RuleView | 围绕一个 View 组织的具体检查、读取或写入规则 |
| Directory | 描述流程阶段、分支（case）与回退（Back）怎样推进 |
| Design ID | 设计节点的稳定身份，贯穿生成代码注释、Binding、测试与文档 |
| Binding | 设计节点与实现符号、测试证据的对应清单，是"设计已实现"的判定依据 |

## 仓库结构

```text
skills/
  dec-design/                 设计规范、模板、YAML/XML 转换器、校验器、HTML 生成器
  dec-implementation/         实现契约、Binding 与测试证据校验
dec-java-lite/                Java 21 多模块框架：解析、编译、Runtime、查询、生成器、CLI
demo/
  order-payment-yaml/         canonical 设计源（10 份 YAML）
  order-payment-xml/          对外导出的 XML
  design-html/                设计阅读站点（HTML）
  order-payment-app/          生成骨架 + 手写应用接入 + 测试
docs/
  cn/                         中文项目文档（含 history 修复记录）
  en/                         英文项目文档
```

## 两个 Skill、一个框架、一个 Demo

- [dec-design](skills/dec-design/SKILL.md)：从需求生成 canonical DEC YAML，并生成 XML/HTML；负责导入与受控同步。
- [dec-implementation](skills/dec-implementation/SKILL.md)：按已校验的 YAML 接入 Runtime、实现适配器、编写测试并维护 Binding 证据。
- [dec-java-lite](dec-java-lite/README.md)：`dec/v1` 的 Java 21 解析、编译、运行时、业务阶段查询与代码生成器。
- [订单支付 Demo](demo/README.md)：独立验证 YAML → XML/HTML → Java 生成与 Binding 的完整闭环。

## 快速开始

```sh
# 1. 准备环境（Python 3 + JDK 21），并安装 Skill 依赖
python3 -m pip install -r skills/dec-design/requirements.txt \
  -r skills/dec-implementation/requirements.txt

# 2. 校验 Demo 设计（重生成派生产物与漂移比对的步骤见开发与验证手册）
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml

# 3. 构建框架并运行全部测试（Demo 测试需自备专用 MySQL）
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install
```

完整的环境说明、生成、运行与证据核对步骤见[开发与验证手册](docs/cn/development-and-verification.md)。

## 当前状态

- 订单支付 Demo：118 个 Design ID 全覆盖、全部 `VERIFIED`，`--require-ready` 通过；支付平台、MongoDB、短信仍为本地替身。
- CI（[verify.yaml](.github/workflows/verify.yaml)）：双 Skill 清单与测试、canonical/binding 校验、XML/HTML 漂移比对、真实 MySQL 行为测试、Binding 证据生成与归档。
- `dec-java-lite` 的 `v0.1.0` 发布与门禁见[发布记录](dec-java-lite/docs/release-p8.md)；实际业务接线状态见[组合验证](demo/INTEGRATION.md)。

## 更多文档

- [项目文档总览](docs/cn/README.md)：理念、整体设计、Skill、Java 框架、示例与开发验证；[XML 修改后同步回 YAML](docs/cn/xml-to-yaml-sync.md)。英文版见 [Documentation](docs/en/README.md)。
- 历史体检与修复快照见[历史记录](docs/cn/history/README.md)（仅中文）。
