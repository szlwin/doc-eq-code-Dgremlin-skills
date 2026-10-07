# 订单支付 DEC 示例

`order-payment-yaml/` 是可独立使用的 `dec/v1` 设计源。`order-payment-xml/` 和 `order-payment-html/` 由 `dec-design` 脚本生成；`order-payment-app/` 是 `dec-java-lite` 生成的 Java 工程。旧的 HTML 反向抽取 YAML 保留在本地 `order-payment-html-recovery/` 作为历史备份，不作为设计输入，也不纳入发布。

在仓库根目录验证：

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml --check
java -jar dec-java-lite/dec-lite-cli/target/dec-lite-cli-0.1.0.jar inspect --yaml demo/order-payment-yaml
java -jar dec-java-lite/dec-lite-cli/target/dec-lite-cli-0.1.0.jar inspect --xml demo/order-payment-xml
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml test
```

设计有 10 个文档、93 个 Design ID、16 个 Information。生成的 Java 工程只验证传输模型、枚举、数据模型和视图代码可编译；Service 在未绑定 DEC Runtime 时明确抛出 `DESIGN_GAP`。业务执行、外部 DataSource 连接和 `smsNotify` 自定义 Action 仍需应用实现与注册。发布验证与这些应用实现是两个不同的完成条件。
