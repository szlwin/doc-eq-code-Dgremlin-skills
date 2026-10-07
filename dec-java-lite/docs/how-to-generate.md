# 生成与验证

从 canonical YAML 生成 Spring Boot/MyBatis 项目；XML 前端用于导入和对等检查，设计修改仍回到 YAML。

```sh
./mvnw clean package
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar inspect --yaml fixtures/mix
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate \
  --yaml fixtures/mix --output examples/order-service/generated \
  --base-package com.example.order
./mvnw -f examples/order-service/generated/pom.xml test
```

`config` 提供 DataSource；`data`、`view`、`api`、`enum` 生成 Application、Entity、嵌套 View DTO、Controller、Service、Request DTO、DAO、Mapper 和 Enum。`rule`、`systems`、`business` 由运行时编译，生成器不会把外部 RuleView 操作伪装为已实现的业务 Java 方法。`RuntimeCatalog` 用注册的 RuleView 和 Custom Action 实现验证运行时就绪。

生成的 Service 在尚未绑定 DEC Runtime 时会抛出带 Design ID 的 `DESIGN_GAP`，不会返回空对象伪装成功。将接口接入实际 RuleView 执行器、事务和业务依赖仍属于项目实现工作。

生成目录由 `.dec-generated` 与 `dec-generated-manifest.txt` 管理。非空且没有标记的目录会拒绝覆盖；旧 manifest 只清理自身列出的安全相对路径；符号链接与越界路径会拒绝。手工文件保留在目录外，生成文件不可手改。`GeneratorPipelineTest` 从 [`fixtures/mix`](../fixtures/mix) 重新生成并与 [`examples/order-service/generated`](../examples/order-service/generated) 逐文件比较。
