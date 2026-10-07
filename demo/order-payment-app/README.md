# 生成的 Java 工程

本目录由 `dec-lite-cli generate --yaml demo/order-payment-yaml --output demo/order-payment-app --base-package com.example.orderpayment` 生成。`.dec-generated` 与 manifest 列出的文件会在重新生成时覆盖；本说明文件不在 manifest 中。

当前 Service 明确抛出带 Design ID 的 `DESIGN_GAP`，直到应用把它接入 DEC Runtime。`mvn test` 证明项目能编译，不能证明订单支付业务已上线。
