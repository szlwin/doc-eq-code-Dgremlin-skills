# Order/payment example

The canonical design is [`fixtures/mix`](../../fixtures/mix). XML under [`fixtures/mix-xml`](../../fixtures/mix-xml) is a generated equivalent used by the parity tests.

`generated/` is produced by `GeneratorPipeline`. Do not edit files there. Regenerate from the project root:

```sh
./mvnw clean install
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate \
  --yaml fixtures/mix --output examples/order-service/generated \
  --base-package com.example.order
./mvnw -f examples/order-service/generated/pom.xml test
```

`GeneratorPipelineTest.checkedInMixExampleMatchesCanonicalGeneratorOutput` checks drift.
The generated `order/information/OrderInformation`, `payment/information/PaymentInformation` and `business/OrderPaymentFlow` classes are read-only projections of the Information tree and Directory flow. They expose design references, dependencies, actions, produces, cases and back edges for navigation; the DEC runtime still owns execution. The generated Service is an interface; an application must provide its implementation and connect it to the DEC runtime.
