# 可运行的订单支付 Demo

本工程由 canonical DEC YAML 生成 Controller、DTO、Entity、View、Enum、DAO 和 Mapper。`src/main/java/com/example/orderpayment/demo/` 是手写应用绑定，不在 `dec-generated-manifest.txt` 中；重新生成不会覆盖它。运行时加载 `src/main/resources/dec/design/` 中由 `dec-design` 正式转换器生成的 XML，编译 RuleView、Information 和 Business Directory，并执行完整订单支付路径。

从代码查设计时，先看生成类的 `DEC <Design ID> from <YAML 文件>` 注释，再用 [DEC-DESIGN-INDEX.md](DEC-DESIGN-INDEX.md) 找到对应的 `../order-payment-yaml/` 文件；手写实现的 Design ID、类和验证用例记录在 `../order-payment-implementation-binding.yaml`。`OrderWorkflow` 只编排 API 和 Directory；`OrderViewAssembler` 通过生成 DAO 读取 Entity 并装配 View，`LocalRuleAdapter` 执行外部 Rule 的 DAO 写入，`DecViewModelBridge` 单独适配 DEC Runtime 所需的动态模型，`LocalOrderStore` 提供本地 Mapper 和提交快照。

在仓库根目录运行：

```sh
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install -DskipTests
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml test
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml spring-boot:run
```

接口执行顺序：

```sh
curl -X POST http://localhost:8080/api/orders/101/submit \
  -H 'Content-Type: application/json' \
  -d '{"status":"1","type":"1","userId":1,"productId":7,"productName":"Demo item","productPrice":14.95,"productCount":2,"remark":"demo-order"}'
curl -X POST http://localhost:8080/api/orders/101/payment
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":"ERROR"}'
curl http://localhost:8080/api/orders/101
curl -X POST http://localhost:8080/api/orders/101/payment/back
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":"SUCCESS"}'
```

`userId=2` 是本地未激活用户样例，支付依赖检查会失败且订单维持已下单状态。其他正整数用户 ID 在本地适配器中视为已激活且已认证。一个提交请求创建一条订单明细，数量由 `productCount` 表示。

此 Demo 的 `data1`、`paymentService`、`mongo1` 与短信动作都由明确的本地内存适配器模拟，便于无需外部服务运行。外部 Rule 通过生成 DAO/Mapper 对本地 Entity 快照执行 insert/update，查询也通过 DAO 装配 View。每次 Directory 调用成功后才发布订单状态、支付错误记录与短信记录；失败不会发布部分状态。数据和通知在应用重启后清空。真实数据库和外部服务连接仍需单独实现。
