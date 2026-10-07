# P8 发布准备与验收记录

## 版本和产物

当前版本为 `0.1.0`。Reactor 包含 `dec-lite-core`、`parser`、`information`、`action`、`session`、`directory`、`query`、`generator`、`cli` 九个模块。`release-artifacts` profile 为各模块附加 sources 与 Javadoc JAR；CLI 同时生成可执行 shaded JAR。生成的应用项目在 `examples/order-service/generated/`，其 Java 源由 `fixtures/mix` 生成。`demo/order-payment-yaml/` 是独立的 canonical 示例输入。

## 已验证门禁（2026-10-07）

| 门禁 | 结果 |
| --- | --- |
| `./mvnw clean verify -Prelease-artifacts,mysql-it` + 临时 MySQL 8.4 | 通过；56 项测试，0 失败、0 错误、0 跳过；两项真实数据库测试验证 Query 连接复用和 order/payment 整体回滚 |
| `release-artifacts` package | 通过；九个模块均有 sources/Javadoc JAR |
| 生成 Spring 项目的 `mvn test` | 通过 |
| XML/YAML 完整 mix、API/Enum digest 与编译对等 | 通过 |
| 成功、失败、Back、三类 Query、权限、并发、热替换、性能、安全测试 | 通过，详情见各测试报告与性能 CSV |
| Demo 设计与实现衔接 | 10 份 YAML、93 个 Design ID、XML digest 对等、38 页 HTML、31 个生成文件；完整结果见仓库 `demo/INTEGRATION.md` |

`mysql-it` profile 在缺少 `DEC_MYSQL_JDBC_URL` 时会失败，避免被跳过的集成测试误报为成功。发布时应使用同一源码提交运行以下命令：

```sh
./mvnw clean verify
DEC_MYSQL_JDBC_URL='jdbc:mysql://host:port/dec_test' DEC_MYSQL_USER=... DEC_MYSQL_PASSWORD=... ./mvnw -Pmysql-it verify
./mvnw -Prelease-artifacts package
./mvnw -f examples/order-service/generated/pom.xml test
```

## 发布与回退

发布提交应同时包含源码、两个 Skill、canonical Demo、生成 XML/HTML 与生成 Java 示例。使用 `v0.1.0` tag 指向该提交，发布九个模块的二进制、sources、Javadoc JAR 与 SHA-256 清单。生成的 Service 在应用未绑定 Runtime 时会抛出 `DESIGN_GAP`；Demo Binding 的 `PLANNED` 条目不表示可部署业务应用。

回退部署时把运行中的 `RuntimeCatalog` 发布指针切回此前已验证的快照或重载上一版输入；旧 Session 继续用旧快照完成。数据库迁移与外部副作用的回退需由应用部署流程单独管理。

源码、测试与版本更新见 [CHANGELOG](../CHANGELOG.md) 和 [迁移说明](migration-p8.md)。
