# Implementation Binding

Binding 是方案 1 的关键产物：它回答“某个 DEC 设计事实由哪些真实代码和测试实现”。

## 基本结构

```yaml
version: dec-binding/v1
bindings:
  - designId: ACT-START-PAY
    strategy: MODIFY
    status: IMPLEMENTED
    implementation:
      - module: order-service
        file: src/main/java/com/acme/order/OrderApplicationService.java
        class: com.acme.order.OrderApplicationService
        method: startPay
        symbol: com.acme.order.OrderApplicationService#startPay
    tests:
      - module: order-service
        file: src/test/java/com/acme/order/OrderApplicationServiceTest.java
        symbol: OrderApplicationServiceTest#startPay
```

## 字段原则

- `designId`：来自 canonical DEC YAML，必须存在且唯一；
- `strategy`：`REUSE | COMPATIBLE_EXTEND | MODIFY | CREATE`；
- `status`：`PLANNED | IMPLEMENTED | VERIFIED | BLOCKED | REMOVED`；
- `implementation`：真实代码位置；不要写自然语言伪路径；
- `tests`：验证该设计行为的真实测试/验证 symbol；
- `runtimeManaged`：DEC Runtime 直接执行时设为 `true`；
- `notes`：仅解释实现事实，不复制业务规则。

## 一对多与多对一

允许一个 Design ID 对应多个 symbol，例如 Action 同时需要 Handler 与 Adapter。也允许多个细粒度 Design ID 由一个领域方法实现。

但 Binding 必须保持显式，不能依赖“名字差不多”推断关系。


## 可信度检查与测试证据

三个层次分别检查，不能把第一个的 PASSED 当作发布就绪：

| 选项 | 检查内容 |
| --- | --- |
| `--require-complete` | 所有 Design ID 有处置，允许 PLANNED/BLOCKED |
| `--require-symbols --project-root ...` | IMPLEMENTED/VERIFIED 的实现与测试 Java 声明确实存在 |
| `--require-evidence` | 当前设计、Binding、源码摘要匹配且声明测试有本次通过报告 |
| `--require-ready` | 隐含完整覆盖、符号与证据检查，且全部 Binding 为 VERIFIED |

`--require-tests` 要求所有 IMPLEMENTED/VERIFIED 节点声明测试，包括 runtimeManaged。PLANNED/BLOCKED 不强求尚未实现的符号与测试证据，但仍阻止发布就绪。JSON 结果分别输出 `coverageComplete`、`symbolsVerified`、`evidenceVerified`、`releaseReady` 和状态计数。

符号检查需要 JDK（本项目使用 21），使用 Java Compiler AST 解析声明，不通过文本包含判断。`file` 相对 `--project-root`，不能越界；`module` 为标识，不隐式拼接路径。支持 `Class`、`Class#method`、全限定类名和 `Outer.Inner#method`；重载实现必须提供源码参数类型签名，例如 `Service#save(String)`。只检查指定文件中直接声明的方法；继承、Lombok/其他代码生成的隐式方法应绑定真实声明或生成源码。非 Java 符号、不可解析源码、歧义不能当作验证成功。

测试报告使用 JUnit XML（Maven Surefire/Failsafe），以解析后的全限定类名和方法名匹配。方法绑定要求报告确实执行该方法，参数化实例都须通过；测试类绑定还要求直接声明的标准 JUnit 测试方法全部出现在报告中，不能用单个方法的部分执行代替整个类。自定义组合注解/动态命名无法对应时应明确绑定可匹配的方法，或扩展验证器；重载测试当前拒绝自动证明。失败、错误、跳过、flaky/rerun 失败记录不能作为该测试通过的证据。

## 生成当前测试证据

不要给已有 XML 补签摘要。用 `run_binding_verification.py` 包装真正的测试命令：

```sh
python3 skills/dec-implementation/scripts/run_binding_verification.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --input ../../dec-java-lite \
  --input ../../skills/dec-implementation \
  -- ../../dec-java-lite/mvnw -q clean test
```

测试命令在 project-root 中执行。先构建并安装本次源码对应的框架依赖；包装器不会自动构建依赖或更改 Binding 状态。`--input` 可重复，用于项目外的框架、生成器或其他依赖源码。Maven 本地缓存、远程服务和环境变化不能仅靠源码摘要证明，测试命令和 CI 应负责固定/构建正确的依赖版本。

默认读取 `target/surefire-reports/*.xml`；可重复传入 `--report-dir` 指定项目内其他报告目录。每份 XML 最大 8 MiB，解析前按 XML 编码识别并拒绝 DTD/ENTITY 声明（含 UTF-16）。报告必须由本次命令产生；残留报告会被拒绝，建议 `clean test`。命令失败、无报告、测试期间输入变化或证据检查不通过时，不保留旧的成功证据。

默认输出 `PROJECT/target/dec-binding-evidence.json`，格式见 `schemas/test-evidence.schema.json`。记录设计、Binding、项目输入、附加依赖输入、各报告的 SHA-256，以及测试命令、开始/结束时间和退出码。验证时重新计算这些摘要并重新解析报告。项目源码摘要覆盖所有文件，排除 target/build、版本库、常见依赖/缓存目录（精确定义见 binding_evidence.py 的 EXCLUDED）；证据必须放在项目 target/build 下。

设计摘要算法 `dec-yaml-json-sha256/v1`：按设计文件相对路径收集 canonical kind 文档，解析 YAML 后对 JSON 键排序、保留列表顺序，再 SHA-256。注释、缩进、映射键排序不会导致失效，业务值、列表顺序或文件路径变化会失效。它是 Binding 验证摘要，不等同于 Java CLI 的 semanticDigest。

```sh
# 核对当前证据，允许尚未完成的节点继续 PLANNED
python3 skills/dec-implementation/scripts/validate_binding.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app --require-complete --require-evidence

# 实现就绪防漂移核对：若仍有 PLANNED、证据过期或存在其他漂移，此检查会失败。
# 当前 Demo 的 118/118 Binding 均为 VERIFIED，且证据新鲜时，该检查应通过。
# 将上面的 --require-complete --require-evidence 替换为 --require-ready
```

证据默认路径可用 `--evidence FILE` 指定；CI 应归档证据与对应 JUnit XML。摘要用于发现漂移，不是防伪签名，也不证明测试断言充分覆盖业务语义；语义符合性仍需 Review。不得为了门禁通过而把 PLANNED 批量改为 VERIFIED。

信任边界：测试命令和产生报告的环境必须可信。包装器执行命令、核对时间与摘要，但任意命令仍可以伪造 JUnit XML 或刷新旧报告的 mtime；本工具不提供报告来源认证。`releaseReady` 表示本工具检查项满足，不替代发布审批、可信 CI 或业务验收。
