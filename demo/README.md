# 订单支付 DEC Demo

业务阶段查询和四种 `find/eq/start/end/with` 示例位于本项目的
[`order-payment-app`](order-payment-app/README.md#业务目录查询)，使用当前 `dec-java-lite` 框架。
入口是 `OrderQueryService` / `OrderQueryExamples`，真实 MySQL 验证为 `OrderQueryTest`。

`order-payment-yaml/` 是 10 份 canonical `dec/v1` 设计源，包含 6 个 API、19 个 Information 和完整订单支付 Directory。`order-payment-xml/` 与 `design-html/` 由 `dec-design` 脚本生成。`order-payment-app/` 包含生成的 Spring 工程和不会被重新生成覆盖的本地业务实现。

在仓库根目录验证：

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml --check
python3 skills/dec-implementation/scripts/validate_binding.py --design demo/order-payment-yaml --binding demo/order-payment-implementation-binding.yaml --project-root demo/order-payment-app --require-complete --require-tests --require-symbols
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install -DskipTests
python3 skills/dec-implementation/scripts/run_binding_verification.py \
  --design demo/order-payment-yaml --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app --input ../../dec-java-lite \
  --input ../../skills/dec-implementation -- ../../dec-java-lite/mvnw -q clean test
python3 skills/dec-implementation/scripts/validate_binding.py \
  --design demo/order-payment-yaml --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app --require-complete --require-evidence
```

业务代码通过 DEC Runtime 执行下单、支付发起、支付成功/失败分类、错误通知、结果回退和查询。生成的 `order/information/OrderInformation`、`payment/information/PaymentInformation` 和 `business/OrderPaymentFlow` 是与信息树、Directory 流程对应的只读设计投影：前者展示 Information 的识别方式、依赖和 change-data，后者展示 Directory、Action、Produce、case 和 Back。运行时仍负责真正执行。参见 `order-payment-app/README.md` 的运行命令与调用样例。生成代码的 Design ID 注释和 `order-payment-app/DEC-DESIGN-INDEX.md` 可回溯到 `order-payment-yaml/`；手写实现由 Binding 定位。Binding 对 118 个 Design ID 全覆盖并全部 `VERIFIED`；四个此前未触发的可选 RuleView/Information 节点已由 `OrderQueryTest` 通过真实 XML、Runtime 和 MySQL 场景覆盖。本地 DAO/Mapper 已用于 Demo 的 Entity 读写，真实外部 DataSource 接入仍需生产集成验证。

`order-payment-html-recovery/` 是历史 HTML 回收时的临时输出目录，已被 Git 忽略，不参与 canonical 设计或 Demo 构建。`order-payment-app/src/main/resources/dec/design/` 是运行时打包用的 XML 副本；CI 会将其与 `order-payment-xml/` 逐文件比较。

HTML 站点入口为 `design-html/index.html`，流程总览为 `design-html/directories.html`，各流程详情位于 `design-html/directory/<business>.html`。在仓库根目录重建：

```sh
python3 skills/dec-design/scripts/render_dec_graph.py demo/order-payment-yaml -o demo/design-html
```

新增流程时将其 canonical 设计目录一并传入该命令，共享页面会汇总所有流程。
