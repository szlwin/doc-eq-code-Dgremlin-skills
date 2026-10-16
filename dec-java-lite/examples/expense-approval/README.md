# 费用审批：第二领域回归场景

设计：`../../fixtures/expense-approval`。费用从 draft → submitted → approved；审核员有效且金额不超过 1000 时允许审批。Information 声明资格与状态，Directory 声明阶段、依赖和状态变化；两个自定义 Action 记录提交和审批，应用不重新实现这些判断。

`generated/` 是生成器输出的可编译工程，包含 Expense Entity/View、Information/Flow 投影、设计索引和 classpath 清单。XML 由官方 converter 派生。它是接线示例，不是具有完整认证/API 的上线审批服务；真实执行接线在 `dec-lite-spring/.../ExpenseApprovalTest.java`，用 H2 费用表和审计表验证事务。fixture 的 approvalDb 为 MySQL 映射，H2 仅为显式测试适配。

从仓库根目录运行：

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py dec-java-lite/fixtures/expense-approval
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install
./dec-java-lite/mvnw -f dec-java-lite/examples/expense-approval/generated/pom.xml test
```

reactor 自动执行 `ExpenseApprovalTest` 的两种事务模式回归和 `ExpenseDesignEvolutionTest` 的设计修改/重新生成/编译/行为对照；CI 另外检查 canonical、XML 漂移及生成工程编译。

重新生成：

```sh
java -jar dec-java-lite/dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate --yaml dec-java-lite/fixtures/expense-approval --output dec-java-lite/examples/expense-approval/generated --base-package com.example.expenses
python3 skills/dec-design/scripts/yaml_to_xml.py dec-java-lite/fixtures/expense-approval -o dec-java-lite/examples/expense-approval/generated/src/main/resources/dec/design --emit-id-comments
```

订单并发、多明细、回调及副作用失败的覆盖和边界见 `../../docs/generality-scenarios.md`。
