# P8 运行时架构

```text
canonical YAML ─┐
               ├─> immutable DecProject ─> Information / RuleView / Directory / Query compilers
DEC XML ────────┘                                │
                                                ▼
                                  RuntimeCatalog atomic RuntimeSnapshot
                                                │
                                  one ExecutionSession per request
                                                │
                          model + Information cache + transaction + Trace
```

`DecYamlParser` 和 `DecXmlParser` 共用 `DecYamlParser.addRoot` 的 canonical AST 装载与 Design ID 检查。`SemanticDigest` 忽略 source path、展示文字与映射顺序，保留 ID、引用、表达式、Action/Produce、权限、图和查询元数据所依赖的字段。完整 mix 的 XML/YAML 对等测试还分别比较 Information 图摘要、RuleView key、Action 绑定、Directory 三种边及 QueryPlan 的路由、Join、谓词和 case。

`RuntimeCatalog.reload()` 在调用线程里完成读取、独立编译、Custom Action/RuleView 就绪检查与 QuerySchema 检查，只有全部成功后才用一次 `AtomicReference.set` 发布。失败不会触碰当前快照。发布前冻结 `DecProject`、嵌套 AST、RuleView 和 Custom Action 注册表；`InformationCompilation` 与 `DirectoryGraph` 持有不可变集合。每次 `RuntimeSnapshot.openSession()` 新建模型副本和 Information 缓存，Session 记录自己打开时的 digest。旧 Session 不会在热替换时被原地修改。调用方应对每个请求只打开一个 Session，并在完成后关闭。

运行语义分属 [Information](information-engine.md)、[Action](action-runtime.md)、[Directory](directory-runtime.md)、[Query](query-runtime.md) 和 [事务](session-transaction-runtime.md)。Directory Back 是一次新的业务路径。Query 使用有界候选集和 PreparedStatement；跨物理路由的原子事务被拒绝。

生成器从同一 `DecProject` 中读取 Config/Data/View/API/Enum，运行时从 Rule/System/Business 编译配置，不存在第二套 declaration 引擎。生成 Java 的漂移由 `GeneratorPipelineTest` 校验。
