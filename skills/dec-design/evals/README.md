# dec-design Agent 级评测

入口 `eval.yaml`，使用 skill-up 0.14.0 的 codex 引擎，保留当前 CLI 的模型配置。依赖 Python 3、Skill requirements 和已登录的 Codex CLI；这里不保存密钥。每个用例在独立临时工作区安装本 Skill，不需要实现 Skill 或主仓库。

| 用例 | 检查重点 |
|---|---|
| chinese-design | 中文费用需求→实体/视图/Information/目录/Custom Action，正式校验及 XML 导出 |
| incremental | 1000→500 的批准上限修改，其他结构和稳定 ID 不变 |
| xml-ambiguity | 无 ID 的外部改名回流产生真实冲突报告，canonical 字节不变 |
| standalone | 单独安装后建立公共枚举并完成校验/导出闭环 |

```sh
skill-up validate skills/dec-design/evals/eval.yaml
skill-up run skills/dec-design/evals/eval.yaml --output-dir /tmp/dec-design-agent-evals --format html
```

`judge.py` 检查实际文件和关联的 tool_call/tool_result，不以最终回答关键词代替执行证据；对设计产物再次运行正式校验和 XML 重建比较。没有轨迹则失败。脚本评分规则只证明列出的契约，不是对任意设计质量的全面判断，也不是对恶意 Agent 的防篡改沙箱。

本地运行会使用当前 CLI 登录和配置，可能带入全局工具/Skill 元数据；因此这里的“独立安装”证明用例不依赖另一个 DEC Skill 或主仓库文件，不等于隔离了宿主配置。需要严格隔离时在专用容器内安装 CLI 并配置凭据。不要把带本机路径的完整 rollout 直接作为公开发布材料。

自动 CI 运行评分器单测、manifest 校验及普通脚本测试。模型评测需显式执行上述命令；`validate` 成功或 Python 单测通过不代表 Agent 用例通过。可用 `--iteration 3` 重复抽样，或 `--baseline` 对比 without_skill；只有一次 with_skill 结果不能证明稳定性或量化收益。

详细执行记录见仓库 [质量增强记录](../../../docs/cn/history/quality-improvements-2026-10-26.md)。

## 评测源码完整性

`evals/` 不安装到被测 Agent，因此不放进安装包的 `FILE_MANIFEST.sha256`。本目录由独立的 `EVAL_MANIFEST.sha256` 管理，覆盖配置、用例、fixtures、judge、测试和说明文件；新增、修改或删除均会让检查失败。CI 同时执行两类清单校验。运行输出应写到仓库外；本地 `iteration-*`、`reports/`、`outputs/`、隐藏文件与 Python 缓存不属于评测源码清单。

修改评测源码后，从仓库根目录运行：

```sh
python3 skills/dec-design/evals/check_manifest.py --write
python3 skills/dec-design/evals/check_manifest.py
```

当前执行证据：[结果摘要](../../../docs/cn/history/quality-agent-evals-2026-10-26.json)、[逐项执行记录](../../../docs/cn/history/quality-improvements-2026-10-26.md)。这些是已发生的模型运行与重评结果，本次完整性检查没有重新调用模型。
