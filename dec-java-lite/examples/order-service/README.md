# Order/payment example

The canonical design is [`fixtures/mix`](../../fixtures/mix). XML under [`fixtures/mix-xml`](../../fixtures/mix-xml) is a generated equivalent used by the parity tests.

`generated/` is produced by `GeneratorPipeline`. Do not edit files there. Regenerate from the project root:

```sh
./mvnw clean package
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate \
  --yaml fixtures/mix --output examples/order-service/generated \
  --base-package com.example.order
./mvnw -f examples/order-service/generated/pom.xml test
```

`GeneratorPipelineTest.checkedInMixExampleMatchesCanonicalGeneratorOutput` checks drift.
The generated Service reports `DESIGN_GAP` until an application connects it to the DEC runtime.
