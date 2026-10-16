# 实现符合性 Review

先检查语义与证据，再核对项目选定的工程约定；偏离 Skill 默认本身不是缺陷。至少检查：

1. Missing implementation；
2. Extra business behavior；
3. Parallel rule；
4. Stale implementation；
5. Wrong owner / wrong DataSource（Rule override/default 解析错误）；
6. Enum drift（relEnum、代码 Enum、持久化/API value 不一致）；
7. API drift（URL/method/required/min/max/length/regex/enum/relEnum/expression/response）；
8. Wrong order/transaction/failure；
9. Workflow/Service/Controller 方法级 `synchronized`：请求协调层不得用方法锁代替 Session、Transaction、DAO/Repository 或 DataSource 的并发边界；
10. Design traceability：类、字段、方法、参数是否能回到 YAML/XML 节点路径，索引是否覆盖；
11. Runtime duplication：Store 是否用枚举/字段组合重写 Information，Workflow 是否硬编码前置状态列表，持久化回读是否复用 Runtime 校验；Workflow 是否重复实现 Runtime 的业务判断和路径规则；项目自定义装配是否保持等价的请求隔离与事务语义；
12. Binding drift；
13. Test gap。
14. DataSource Store lifecycle：实际装配入口（Factory、Spring Bean 或其他 DI）、作用域、共享状态、线程安全和提交边界是否明确；不以是否有 Factory 或 @Component 判定正确性；只校验状态的方法是否误命名为 `commit`、是否存在没有资源生命周期含义的 `completed` 标记；完成回调中的写回和校验是否仍处于真实事务边界内，异常是否向事务所有者传播。

15. API grouping：Controller/Service/实现类是否遵循本项目选定的默认或配置分组；配置是否独立于业务设计、是否仍保留 System 语义归属；方法级 Design ID、DTO、路由、Binding、配置输入与生成 manifest 是否同步。不把 URL 首资源或具体包名当作业务硬要求。

16. Custom Action：设计名与注册 key 是否匹配，是否有真实实现及统一注册入口，Workflow 是否依赖具体 Action；外部副作用、Produce 和事务提交时序是否符合声明，日志替身是否被误标成真实短信投递。

测试通过不自动证明符合性；测试可能只验证了当前代码，而没有验证代码是否实现正确设计。

17. 事务接入：Runner 是否显式选择模式，是否仍传空 ActionTransaction/null；外部事务是否校验有效性并标记回滚，自管模式是否混入已有外部事务；最终写回与一致性校验是否在物理提交前；是否覆盖两种模式的写回/校验/提交失败，并正确区分 EXTERNAL_PENDING、COMMITTED 与提交结果不确定。

18. Binding 可信度：Java 类/方法是否通过 AST 确认；声明测试是否匹配本次 JUnit 报告；设计、Binding、源码/依赖及报告摘要是否仍匹配；是否错误地把完整覆盖或部分测试类执行当作发布就绪。PLANNED 必须如实保留，不自动改为 VERIFIED。
