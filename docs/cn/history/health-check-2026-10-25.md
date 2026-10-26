# 项目检查报告：2026-10-25

检查基线：`75412f9f4f61132e72a575a470bc443bb9c9e8cc`。开始时工作区无未提交变更。本次检查文档、两个 Skill、Java 框架及订单支付 Demo；未修改实现、设计或清单，只新增本报告。边界复现使用临时文件和临时工程副本。

## 结论

项目已经形成可运行的设计、生成、执行和证据闭环：canonical 输入、派生产物和订单 Demo 主流程一致，真实 MySQL 测试及当前 Binding 就绪校验通过。但存在 3 个已复现的功能问题、1 个直接阻断 CI 的清单问题，以及 CI 门禁和文档维护缺口。当前测试通过不能覆盖这些新增发现。

## 已确认的问题

### F1 · P1：生成器可能覆盖未受管的手写代码

位置：[GeneratedProjectWriter.java](../../../dec-java-lite/dec-lite-generator/src/main/java/com/dec/lite/generator/GeneratedProjectWriter.java)，`writeTemplate` 第 37–40 行、`prepareOutput` 第 69–77 行。

现有 manifest 仅用于删除旧受管文件；写入新目标时使用 `TRUNCATE_EXISTING`，没有检查已经存在的文件是否属于旧 manifest。因此，设计新增类型或 API 分组变更产生同名文件时，手写代码会被静默覆盖。

复现：在临时目录按默认配置生成订单工程；创建不在 manifest 中的 `order/service/OrderPaymentsService.java`，写入手写标记；用仓库的 `demo/order-payment-generation.properties` 再生成。命令退出码为 0，但手写标记被生成接口替换。

建议：生成前计算完整目标路径集合，拒绝覆盖旧 manifest 之外的现存文件，并列明冲突路径；先在暂存目录完成渲染、校验，再提交文件变更。补充“API 分组变化撞上手写文件”和“生成中途失败保留旧输出”的回归。

### F2 · P1：查询谓词下推改变字符串字面量，造成漏查

位置：[InformationPredicateCompiler.java](../../../dec-java-lite/dec-lite-query/src/main/java/com/dec/lite/query/InformationPredicateCompiler.java)，第 38 行。

`definition.ruleData().replaceAll("\\s+", " ")` 对整条表达式压缩空白，也修改引号内的业务数据。

复现：使用现有 mix 模型编译 `user.name = 'a  b'`（中间两个空格）。`ModelExpressionCompiler` 对相同值返回 `TRUE`，但下推结果是 `info1:string=a b`（一个空格），并标记为 `FULL`。数据库中正确的 `a  b` 记录可能在候选查询阶段被排除，后续 Runtime 过滤无法找回。

建议：从已解析表达式 AST 生成 SQL 谓词，或至少采用保留字符串字面量的词法处理；遇到无法证明等价的表达式退回 Runtime。补充连续空格、引号内关键字、转义字符串等语义对等测试。

### F3 · P2：Demo 将基础设施故障统一转换成 HTTP 409

位置：[OrderWorkflow.java](../../../demo/order-payment-app/src/main/java/com/example/orderpayment/order/workflow/OrderWorkflow.java)，第 174–175 行；异常分类还涉及 Action 和 Directory Runtime。

`DirectoryWorkflowException` 与 `DirectoryExecutionException` 被统一映射为 `CONFLICT`，没有区分业务状态冲突和外部能力失败。

复现：在临时 Demo 副本中通过 Spring `SpyBean` 使 `PayDao.insert` 抛出 `DataAccessResourceFailureException`；提交订单后调用支付接口，实际得到 409。要求 5xx 的故障注入测试失败，输出为 `409 expected:<SERVER_ERROR> but was:<CLIENT_ERROR>`。

影响：客户端会把数据库不可用误判为不可推进的业务状态，重试策略、告警统计和问题定位都会受影响。最终写回或提交阶段的异常可能走另一条路径，不能以这些路径返回 5xx 证明 Action 内部故障也被正确分类。

建议：贯穿 Adapter、ActionResult、SessionError 保留结构化故障类型和必要的 cause；仅把业务重复、非法推进等映射为 409，基础设施失败映射为适当的 5xx。添加 HTTP 层故障注入回归，同时验证事务回滚。

### F4 · P1：dec-implementation 清单失效，当前 CI 会提前失败

位置：[FILE_MANIFEST.sha256](../../../skills/dec-implementation/FILE_MANIFEST.sha256)，第 16 行。

运行 `python3 skills/dec-implementation/scripts/check_manifest.py` 返回 1。唯一差异是 `references/rule-action-implementation.md` 的摘要：清单记录 `e8880f...`，当前内容实际为 `bc24a0...`。

[CI](../../../.github/workflows/verify.yaml) 在执行后续 Java 和 Demo 验证前检查清单，因此当前提交无法完整通过这条流水线。Skill 的 32 项 Python 测试通过并不抵消这一独立门禁失败。

建议：先审阅该文档变更，确认内容正确后使用官方 `check_manifest.py --write` 刷新并重新校验；将清单检查加入提交前验证。本次仅报告，未重写清单。

### F5 · P2：CI 没有强制全部 Binding 达到 VERIFIED

位置：[verify.yaml](../../../.github/workflows/verify.yaml)，第 78 行；[validate_binding.py](../../../skills/dec-implementation/scripts/validate_binding.py)，第 166–172、205–206 行。

CI 使用 `--require-complete --require-evidence`，没有使用 `--require-ready`。前者允许 `PLANNED/BLOCKED`；这些条目只产生警告，并不按已实现节点强制测试证据。包装器也未强制 ready。因而未来将节点降为 PLANNED 后重新生成匹配证据，完整覆盖和证据检查仍可能通过，但 `releaseReady=False`。

当前 118 个节点确实通过本次 `--require-ready`，所以这是一项防回退门禁缺口，不是声称当前 Binding 虚假。

建议：如果 CI 承担 README 宣称的完整 Demo 就绪保证，末尾改用 `--require-ready`；若开发分支允许未完成节点，则明确区分开发检查和发布就绪门禁。

### F6 · P3：发布文档中的“当前状态”落后于源码

位置：[release-p8.md](../../../dec-java-lite/docs/release-p8.md)，第 5、18 行。

文档仍称当前 Reactor 有九个模块，遗漏 `dec-lite-spring`，实际为十个；仍称“当前 Demo”有 111 个 Design ID，实际校验为 118。文档中的 2026-10-07 历史验收表已明确标注日期，应保留历史事实，问题是其旁边继续维护的“当前”描述。

建议：历史文档只保留当时快照，当前数量统一链接到一处状态页，或由构建生成；避免 README、发布记录、集成说明多处手工维护相同数字。

## 验证结果

| 检查 | 本次结果 |
| --- | --- |
| dec-design 文件清单 | 通过，100 个文件 |
| dec-implementation 文件清单 | 失败，1 个文件摘要不同，见 F4 |
| dec-design Python 测试 | 90 项：首轮 89 通过、1 项因 Node 不在 PATH 跳过；加入宿主自带 Node 后补跑渲染测试 20 项全部通过，覆盖该跳过项 |
| dec-implementation Python 测试 | 32 项通过 |
| Java 框架常规 verify | 200 项，其中 4 项 MySQL 测试按条件跳过 |
| Java 框架 install -Pmysql-it | 使用独立 MySQL 8.4 容器，200 项通过，0 失败、0 错误、0 跳过 |
| 订单 Demo 真实 MySQL 测试 | 42 项通过，0 失败、0 错误、0 跳过 |
| canonical Demo 校验 | 10 份文件、118 个 Design ID，通过 |
| Binding 基础及 --require-ready | 118/118，符号和本次测试证据有效，`releaseReady=True` |
| XML/HTML 重生成比对 | 导出 XML、应用内 XML、38 页 HTML 全部无差异 |
| Markdown 本地目标路径 | 扫描 107 份 Markdown，未发现失效文件目标；不包含外部 URL 可用性和全部锚点正确性 |
| expense-approval 生成工程 mvn test | 构建成功，但输出 `No tests to run`；其运行时行为测试位于框架，不能把此命令当作生成应用行为测试 |
| 新增边界复现 | F1、F2 复现成功；F3 的预期 5xx 断言失败，实际为 409 |

临时 MySQL 仅用于本次检查，测试结束已停止并自动删除，没有使用项目默认数据库。未进行远程 CI 执行、发布、生产负载测试或真实支付/MongoDB/短信集成验证。

## 建议完善的方向

1. **增加 Agent 级 Skill 评测。** 两个 Skill 有脚本单测，但没有发现 `evals/eval.yaml` 或对应任务用例。本次参考 `skill-upper` 的评测区分方法做检查，没有执行模型评测；本机也未发现 `skill-up` 命令。建议覆盖：从中文需求设计、增量改动、XML 歧义回流、DESIGN_GAP、独立安装、Runtime 不支持时的诚实降级、PLANNED/VERIFIED 证据边界。检查最终产物和实际调用轨迹，不只匹配回答关键词。
2. **建立跨实现语义对等回归。** Python validator/converter、Java parser/compiler、Runtime、查询下推及生成模板共同解释设计。现有 fixture 对等测试值得保留，再补变体和反例，优先覆盖字符串、数值、null、枚举和关系集合；F2 是这种测试的直接收益点。
3. **把生成器所有权保护作为框架契约。** 明确哪些文件可以覆盖、何时必须拒绝、失败是否恢复，提供 dry-run/变更清单；API 分组迁移不仅是生成成功，还应检查手写实现和旧 Bean 的迁移。
4. **为生成工程补启动和接线测试。** expense-approval 及可配置分组工程的 `mvn test` 当前更接近编译检查。可增加实际装配所需 Bean 后的 Spring 启动测试和一条完整业务路径，验证资源清单、XML 打包与服务实现接线。
5. **明确从 Demo 到生产的验收清单。** 文档已经坦诚声明支付平台、MongoDB、短信是本地替身，也说明了 Back 后迟到回调和短信 afterCommit 不保证重试。这些不是新发现的缺陷。若准备生产化，应补 paymentAttemptId/callbackId、幂等与迟到事件策略、Outbox 或等效可靠投递、真实适配器契约、数据库迁移及负载验证。

建议顺序：先修 F4 恢复 CI；随后修 F1/F2 并增加针对性回归；再修 F3 和补 F5 门禁；最后收敛文档事实源并建设 Skill 评测。无需为解决这些问题重构整个框架。
