# 订单支付 DEC Demo

`order-payment-yaml/` 是 10 份 canonical `dec/v1` 设计源，包含 5 个 API、16 个 Information 和完整订单支付 Directory。`order-payment-xml/` 与 `order-payment-html/` 由 `dec-design` 脚本生成。`order-payment-app/` 包含生成的 Spring 工程和不会被重新生成覆盖的本地业务实现。

在仓库根目录验证：

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml --check
python3 skills/dec-implementation/scripts/validate_binding.py --design demo/order-payment-yaml --binding demo/order-payment-implementation-binding.yaml --project-root demo/order-payment-app --require-complete --require-tests
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install -DskipTests
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml test
```

业务代码通过 DEC Runtime 执行下单、支付发起、支付成功/失败分类、错误通知、结果回退和查询。参见 `order-payment-app/README.md` 的运行命令与调用样例。生成代码的 Design ID 注释和 `order-payment-app/DEC-DESIGN-INDEX.md` 可回溯到 `order-payment-yaml/`；手写实现由 Binding 定位。Binding 对 107 个 Design ID 全覆盖，其中 103 个 `VERIFIED`、4 个 `PLANNED`；后者是当前场景未触发的可选 RuleView/Information 节点。本地 DAO/Mapper 已用于 Demo 的 Entity 读写，真实外部 DataSource 接入仍需生产集成验证。

`order-payment-html-recovery/` 是历史 HTML 回收时的临时输出目录，已被 Git 忽略，不参与 canonical 设计或 Demo 构建。`order-payment-app/src/main/resources/dec/design/` 是运行时打包用的 XML 副本；CI 会将其与 `order-payment-xml/` 逐文件比较。
