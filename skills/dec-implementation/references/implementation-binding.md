# Implementation Binding

Binding 是方案 1 的关键产物：它回答“某个 DEC 设计事实由哪些真实代码和测试实现”。

## 基本结构

```yaml
version: dec-binding/v1
bindings:
  - designId: ACT-START-PAY
    strategy: MODIFY
    status: IMPLEMENTED
    implementation:
      - module: order-service
        file: src/main/java/com/acme/order/OrderApplicationService.java
        class: com.acme.order.OrderApplicationService
        method: startPay
        symbol: com.acme.order.OrderApplicationService#startPay
    tests:
      - module: order-service
        file: src/test/java/com/acme/order/OrderApplicationServiceTest.java
        symbol: OrderApplicationServiceTest#startPay
```

## 字段原则

- `designId`：来自 canonical DEC YAML，必须存在且唯一；
- `strategy`：`REUSE | COMPATIBLE_EXTEND | MODIFY | CREATE`；
- `status`：`PLANNED | IMPLEMENTED | VERIFIED | BLOCKED | REMOVED`；
- `implementation`：真实代码位置；不要写自然语言伪路径；
- `tests`：验证该设计行为的真实测试/验证 symbol；
- `runtimeManaged`：DEC Runtime 直接执行时设为 `true`；
- `notes`：仅解释实现事实，不复制业务规则。

## 一对多与多对一

允许一个 Design ID 对应多个 symbol，例如 Action 同时需要 Handler 与 Adapter。也允许多个细粒度 Design ID 由一个领域方法实现。

但 Binding 必须保持显式，不能依赖“名字差不多”推断关系。
