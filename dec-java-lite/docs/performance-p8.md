# P8 性能基线

`PerformanceBaselineTest` 在 `fixtures/mix`（16 个 Information、5 个 Directory）上测量解析和编译、首次与增量识别、Directory 路径、Query 编译、并发读取以及快照堆内存增量。每次验证写入 `dec-lite-query/target/performance/p8-baseline.csv`。CI 应归档该文件并比较相同机器规格的历史趋势；单机耗时不能跨不同硬件直接比较。

2026-10-07 本机一次 `./mvnw clean verify` 样本：parse + compile 约 16 ms/次，首次 Information 约 0.5 ms，增量重算约 0.06 ms/次，Directory 约 0.11 ms/路径，Query 编译约 0.10 ms/次，并发读取约 0.02 ms/查询，快照分配时的粗略堆增量约 4.9 MB。测试设有宽松发布护栏：完整解析编译 < 5 s，其余单次操作 < 1 s，快照增量 < 128 MB。该护栏用于发现数量级退化；小幅性能变化应看连续 CI 趋势。

测量不包含真实网络、数据库延迟或 JVM 长时间稳态吞吐；真实 MySQL 行为由 `mysql-it` 验证。
