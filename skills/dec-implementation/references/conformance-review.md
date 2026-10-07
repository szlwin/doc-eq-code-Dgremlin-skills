# 实现符合性 Review

至少检查：

1. Missing implementation；
2. Extra business behavior；
3. Parallel rule；
4. Stale implementation；
5. Wrong owner / wrong DataSource（Rule override/default 解析错误）；
6. Enum drift（relEnum、代码 Enum、持久化/API value 不一致）；
7. API drift（URL/method/required/min/max/length/regex/enum/relEnum/expression/response）；
8. Wrong order/transaction/failure；
9. Binding drift；
10. Test gap。

测试通过不自动证明符合性；测试可能只验证了当前代码，而没有验证代码是否实现正确设计。
