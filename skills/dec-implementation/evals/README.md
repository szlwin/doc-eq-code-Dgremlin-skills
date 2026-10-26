# dec-implementation Agent 级评测

入口 `eval.yaml`，使用 skill-up 0.14.0 的 codex 引擎与当前 CLI 模型配置。依赖 Python 3、Skill requirements 和已登录的 Codex CLI，不在仓库保存密钥。fixture 仅有 canonical 费用设计及明确的 Runtime 缺失说明，不依赖设计 Skill 或主仓库。

| 用例 | 检查重点 |
|---|---|
| design-gap | 未声明 ACT-REFUND 时报告 DESIGN_GAP，不猜测退款业务、不改 canonical |
| runtime-gap | 缺少 Runtime/驱动时诚实 BLOCKED，不静默替换 MySQL；Binding 引用已知节点 |
| evidence-boundary | 独立读取设计，计划保持 PLANNED；实际尝试就绪门禁并拒绝无证据状态 |

```sh
skill-up validate skills/dec-implementation/evals/eval.yaml
skill-up run skills/dec-implementation/evals/eval.yaml --output-dir /tmp/dec-implementation-agent-evals --format html
```

评分读取真实 Binding、缺口文件、设计结构和工具轨迹，并重新执行 Binding 校验。允许为额外已知节点补充同样未就绪的 Binding；不允许虚构节点、实现或测试证据。最终回答里提到“已校验”不能替代执行。

这里重点验证缺口和证据边界，尚不证明 Agent 能稳定完成复杂的端到端 Java 实现。下一批可增加真实 Runtime 工程、正确实现与失败实现的成对用例，再进行重复抽样及 without_skill 对照。测试中的 `PLANNED/VERIFIED` 判断也不替代可信 CI 的测试来源保证。

本地引擎会继承宿主 CLI 配置；独立安装验证不等于隔离所有全局元数据。严格隔离需专用容器及凭据。普通 CI 只运行评分器单测、manifest 和脚本测试，模型运行必须另行执行；命令失败或模型未启动要记录 ERROR，不能按 Skill FAIL 或 PASS 汇总。

执行记录见仓库 [质量增强记录](../../../docs/cn/history/quality-improvements-2026-10-26.md)。

## 评测源码完整性

`evals/` 不安装到被测 Agent，因此不放进安装包的 `FILE_MANIFEST.sha256`。本目录由独立的 `EVAL_MANIFEST.sha256` 管理，覆盖配置、用例、fixtures、judge、测试和说明文件；新增、修改或删除均会让检查失败。CI 同时执行两类清单校验。运行输出应写到仓库外；本地 `iteration-*`、`reports/`、`outputs/`、隐藏文件与 Python 缓存不属于评测源码清单。

修改评测源码后，从仓库根目录运行：

```sh
python3 skills/dec-implementation/evals/check_manifest.py --write
python3 skills/dec-implementation/evals/check_manifest.py
```

当前执行证据：[结果摘要](../../../docs/cn/history/quality-agent-evals-2026-10-26.json)、[逐项执行记录](../../../docs/cn/history/quality-improvements-2026-10-26.md)。这些是已发生的模型运行与重评结果，本次完整性检查没有重新调用模型。
