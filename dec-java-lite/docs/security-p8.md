# P8 安全边界

- XML 前端禁止 DTD、外部实体、XInclude、外部 Schema 和未知语义属性；XXE 测试会尝试读取本地文件并要求解析失败。
- YAML 使用 SnakeYAML `SafeConstructor`，禁止自定义 Java 对象标签、重复键；限制别名数、嵌套深度与单文件大小。
- QueryPlan 的字段和表来自编译后的 Data/View 元数据；值进入 `TypedParameter` 并经 `PreparedStatement` 绑定。敏感参数在 SQL explain 中遮蔽。
- Information 和 API expression 由受限语法解析，不执行脚本、反射或任意类加载。ModelContext 路径为受限标识符/索引语法；System 写权限在 Action 与 Change 阶段检查。
- Custom Action 与 RuleView 只通过显式注册表进入运行时；发布后的注册表冻结。`RuntimeCatalog` 在发布前验证引用均已绑定。
- 生成器检查 Java package、输出相对路径与符号链接，避免输出目录穿越。生成文件只由 manifest 管理；不得把包含密钥的生产配置写进提交的 fixture。

这些是当前代码的边界，不代替应用层认证、授权、数据库账号最小权限或真实业务 Action 的安全审查。
