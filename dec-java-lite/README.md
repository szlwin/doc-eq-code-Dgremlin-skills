# dec-java-lite

DEC `dec/v1` 的 Java 21 实现，包含 YAML/XML 前端、Information/Action/Directory/Query 运行时，以及 Spring Boot 3.2.4 + MyBatis 项目生成器。两个前端进入同一 canonical AST 和编译链；YAML 是设计源，XML 可作为等价输入。正式端到端契约在 [`fixtures/mix`](fixtures/mix)，生成 XML 在 [`fixtures/mix-xml`](fixtures/mix-xml)。

## 快速开始

```sh
./mvnw clean verify
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar inspect --yaml fixtures/mix
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar inspect --xml fixtures/mix-xml
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate \
  --yaml fixtures/mix --output examples/order-service/generated \
  --base-package com.example.order
./mvnw -f examples/order-service/generated/pom.xml test
```

`inspect` 输出稳定 Design ID 与语义 digest。`information`、`action`、`directory`、`query` 命令也接受 `--yaml` 或 `--xml`。生成目录带 `.dec-generated` 和 manifest；不要手工修改生成文件，`GeneratorPipelineTest` 会检查漂移。

真实 MySQL 验证需先设置 `DEC_MYSQL_JDBC_URL`、`DEC_MYSQL_USER`、`DEC_MYSQL_PASSWORD`，再运行 `./mvnw -Pmysql-it verify`。发布源代码与 Javadoc 包使用 `./mvnw -Prelease-artifacts package`。

## 文档

- [输入与前端](docs/canonical-input.md)
- [运行时架构及热替换](docs/architecture.md)
- [Information](docs/information-engine.md)、[Action](docs/action-runtime.md)、[Directory](docs/directory-runtime.md)、[Query](docs/query-runtime.md)、[事务与 Session](docs/session-transaction-runtime.md)
- [生成代码](docs/how-to-generate.md)、[迁移说明](docs/migration-p8.md)、[性能基线](docs/performance-p8.md)、[发布准备](docs/release-p8.md)

历史 `dec-expand-declaration` 已整体退役；本项目不提供兼容适配器。`REQUIRES_NEW`、跨数据源原子提交和未注册的自定义 Action 均不属于当前运行时契约。
