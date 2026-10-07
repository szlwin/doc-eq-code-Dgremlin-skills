# dec-design → dec-implementation → dec-java-lite 验证

验证输入是 `order-payment-yaml/` 的 10 份 canonical `dec/v1` 文件。旧 `html-recovery` YAML 只有页面文本和导航，不是设计事实；本次按现有 canonical mix 重建，并由同一设计源生成 XML 与 HTML。

| 环节 | 结果 |
| --- | --- |
| `dec-design` 校验 | 10 文件、93 个稳定 Design ID、16 个 Information；0 错误、0 警告 |
| YAML → XML | 10 文件；`dec-id` 注释保留身份；`dec-java-lite` 读取 XML 与 YAML 的 digest 相同 |
| YAML → HTML | 38 页面；跨进程顺序已固定，重复生成逐文件一致 |
| `dec-java-lite` 代码生成 | 31 个受 manifest 管理的文件；生成 Spring 工程可编译 |
| `dec-implementation` Binding | 93/93 个 Design ID 有条目；当前均为 `PLANNED`，反映应用业务接线尚未完成 |

## 组合效果与剩余工作

设计侧的字段、引用、Information、Directory、XML/HTML 投影以及 Java 结构生成已经打通。`mvn test` 验证生成代码可编译，但没有把 `SubmitOrderService` 接入 RuleView/Directory 执行、真实 DataSource 或事务；该方法会明确抛出 `DESIGN_GAP API-ORDER-SUBMIT`。`ACT-SMS-NOTIFY` 还需要项目实现并注册自定义 Action。`implementation-binding.yaml` 把这些条目标为 `PLANNED`，没有宣称业务已经验证。

本次发现并修正了两个衔接问题：原 demo YAML 是 HTML 反向抽取物，不可作为实现输入；HTML Information 页受 Python hash seed 影响，导致生成漂移。生成器的空对象成功返回也已改为显式失败。

`dec-implementation` 已增加独立于 ID 覆盖率的 `--require-ready` 就绪门禁。`--require-complete` 只证明 93 个 ID 都有 Binding；`--require-ready` 会拒绝当前的 `PLANNED` 条目。应用准备上线时还需检查真实代码、注册表和场景测试，并让相关 Binding 达到 `VERIFIED`。`dec-design` 与 `dec-implementation` 保持独立使用；本示例中的 Runtime 检查仅在目标项目提供 `dec-java-lite` 时执行。
