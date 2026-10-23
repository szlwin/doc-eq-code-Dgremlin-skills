# 开发与验证手册

[返回文档总览](README.md)

这份手册按一次真实改动来写：先检查设计，再生成应用，最后用测试和 Binding 证据确认结果。除特别注明外，命令都从仓库根目录执行。XML 已被外部工具修改时，先看 [同步指南](xml-to-yaml-sync.md)，不要先重生成把修改覆盖掉。

## 1. 先准备环境

- Python 3，安装两个 Skill 的依赖：PyYAML 6.x、jsonschema 4.x。
- JDK 21，`JAVA_HOME` 指向对应 JDK。
- 使用仓库 Maven Wrapper；首次构建需要可下载 Maven 和依赖。
- 运行订单 Demo 和真实数据库测试需要 MySQL 及专用数据库。

```sh
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r skills/dec-design/requirements.txt \
  -r skills/dec-implementation/requirements.txt
java -version
./dec-java-lite/mvnw -version
```

## 2. 先检查设计，再生成派生产物

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml --check
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o demo/order-payment-xml --emit-id-comments
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o demo/order-payment-app/src/main/resources/dec/design --emit-id-comments
python3 skills/dec-design/scripts/render_dec_graph.py demo/order-payment-yaml -o demo/design-html
```

结构/引用校验成功不等于 Runtime 能执行所有扩展。新增 Rule、Action 或 DataSource 时，需同时核对编译器能力和实际适配器。

## 3. 构建框架并生成应用骨架

```sh
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install -DskipTests
```

构建成功后，从实际产物定位 CLI（排除源码、Javadoc 和测试附件）：

```sh
# 选择唯一的 CLI 主产物，排除源码和 Javadoc 附件；多版本残留时停止并检查 target。
dec_cli_jar=$(python3 - <<'PYCLI'
from pathlib import Path
jars = sorted(p for p in Path('dec-java-lite/dec-lite-cli/target').glob('dec-lite-cli-*.jar')
              if not p.name.endswith(('-sources.jar', '-javadoc.jar', '-tests.jar')))
if len(jars) != 1:
    raise SystemExit(f'需要唯一 CLI jar，实际找到 {len(jars)} 个；请核对构建结果后重试')
print(jars[0])
PYCLI
)
```

定位成功后，在同一 shell 中执行；若提示找不到或有多个 jar，先核对构建结果和旧版本残留。

```sh
java -jar "$dec_cli_jar" inspect \
  --yaml demo/order-payment-yaml
java -jar "$dec_cli_jar" generate \
  --yaml demo/order-payment-yaml \
  --output demo/order-payment-app \
  --base-package com.example.orderpayment
```

这里的 `-DskipTests` 只用于先安装依赖，不是测试通过的证明。生成器维护自己的 manifest 范围，手写代码仍需按改变后的接口进行调整。XML 由上一节的 Converter 生成，不会由 Java 生成器自动从任意路径收集。

要试验独立 API 分组配置，使用一个新的输出目录：

```sh
java -jar "$dec_cli_jar" generate \
  --yaml demo/order-payment-yaml \
  --output /tmp/dec-configured-demo \
  --base-package com.example.orderpayment \
  --generation-config demo/order-payment-generation.properties
```

配置样例会拆出支付子资源组。试验目录是生成骨架，不会自动复制当前 Demo 的手写实现；启用到正式工程时要迁移 ServiceImpl/Bean/Binding。

## 4. 跑一遍订单支付 Demo

先在 MySQL 中准备专用数据库并设置连接参数：

```sh
export DEC_DB_URL='jdbc:mysql://127.0.0.1:3306/dec_query_demo?useSSL=false&allowPublicKeyRetrieval=true'
export DEC_DB_USERNAME='root'
# 在本地环境设置 DEC_DB_PASSWORD，避免把凭据写进命令记录或文档。
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml spring-boot:run
```

示例 URL 用于本地开发。应用启动执行 schema 初始化，创建 `dec_demo_*` 表；不会自动创建 URL 中不存在的数据库。正式环境的连接、迁移和凭据管理由部署方案确定。

一条失败后回退再成功的演示路径：

```sh
curl -X POST http://localhost:8080/api/orders/101/submit \
  -H 'Content-Type: application/json' \
  -d '{"status":1,"type":1,"userId":1,"productId":7,"productName":"Demo item","productPrice":14.95,"productCount":2,"remark":"demo-order"}'
curl -X POST http://localhost:8080/api/orders/101/payment
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":2}'
curl http://localhost:8080/api/orders/101
curl -X POST http://localhost:8080/api/orders/101/payment/back
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":1}'
```

使用尚未占用的订单 ID；已有数据可能触发重复操作检查。`resultCode` 1 为成功，2 为失败。业务查询调用见 [Demo 查询示例](order-payment-demo.md#7-用业务阶段查询订单)。

## 5. 根据改动选择验证范围

| 改动 | 应执行的验证 |
| --- | --- |
| 纯说明文档 | 链接、命令、代码路径和事实核对 |
| YAML 字段/引用/规则 | 设计校验、Converter、Java 编译、相关行为测试 |
| XML 同步 | 预检查、实际合并后的目录校验、重新生成和相关测试 |
| HTML 渲染器 | 渲染测试、重生成漂移检查、浏览器交互检查 |
| Java 生成器/模板 | 生成器测试、快照、生成工程编译、适用的接口测试 |
| 事务/适配器/查询 | 框架测试、真实数据库场景、失败与回滚测试 |
| Binding/实现声明 | 完整覆盖、Java 符号、本次测试报告与摘要证据 |

常用测试命令：

```sh
python3 -m unittest discover -s skills/dec-design/tests
python3 -m unittest discover -s skills/dec-implementation/tests
./dec-java-lite/mvnw -f dec-java-lite/pom.xml verify
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml test

# 只验证业务查询集成，仍需要真实 Demo MySQL。
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml -Dtest=OrderQueryTest test
```

Demo 测试会清理 `dec_demo_*` 数据，必须连接专用测试库。框架的真实 MySQL 测试使用另一组环境变量：`DEC_MYSQL_JDBC_URL`、`DEC_MYSQL_USER`、`DEC_MYSQL_PASSWORD`，配置后执行：

```sh
./dec-java-lite/mvnw -f dec-java-lite/pom.xml -Pmysql-it verify
```

未提供数据库环境时，一些条件测试可能跳过；只有实际运行报告能证明真实 MySQL 路径已验证。不要把本地内存测试结果归因到数据库驱动。

### 并发测试应证明什么

并发回归需要确定性地制造竞争窗口，并核对失败后的持久化结果。[OrderApiTest](../../demo/order-payment-app/src/test/java/com/example/orderpayment/demo/OrderApiTest.java) 的 `concurrentHttpSubmissionsReturnOneSuccessAndOneConflictWithoutPartialWrites` 用 barrier 让两个请求都读到“订单不存在”后再继续，避免实际只测到顺序重复请求。

测试覆盖相同新用户、相同已有用户、不同新用户争抢同一订单 ID，分别断言 HTTP 状态为一成功、一冲突（200/409），且最终只有一笔订单、一条明细和一个用户；不同新用户场景还验证失败请求的用户初始化被回滚。这种“竞争窗口 + 接口结果 + 数据完整性”的组合，比只断言抛出异常更能说明事务行为。

修改冲突映射时还要区分业务重复与基础设施故障：数据库主键冲突可按业务契约返回 409，网络或提交故障不能因此一概转成 409。测试依赖真实 MySQL，执行条件与数据库清理要求同本节说明。

## 6. 用 Binding 说明“谁实现了什么”

基础检查：

```sh
python3 skills/dec-implementation/scripts/validate_binding.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --require-complete --require-tests --require-symbols
```

这会检查设计覆盖、测试声明和 Java 声明存在，但不等于这些测试刚刚通过。测试证据应由包装器执行真实测试后生成：

```sh
python3 skills/dec-implementation/scripts/run_binding_verification.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --input ../../dec-java-lite \
  --input ../../skills/dec-implementation \
  --input ../order-payment-generation.properties \
  -- ../../dec-java-lite/mvnw -q clean test

python3 skills/dec-implementation/scripts/validate_binding.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --require-complete --require-evidence
```

包装器的测试命令和 `--input` 相对 `project-root` 解析；先安装本次源码对应的框架依赖。最后一个附加输入包含生成配置，即使当前使用默认分组，也可将该工程配置文件纳入变更追踪。

默认证据为应用 `target/dec-binding-evidence.json`，关联设计、Binding、源码、附加依赖和 JUnit XML 摘要。报告必须来自本次命令，失败/跳过不作为通过证据。Java 符号检查使用 AST，方法重载需要明确签名。

| 参数 | 能证明什么 |
| --- | --- |
| `--require-complete` | 每个 Design ID 有处置，允许 PLANNED/BLOCKED |
| `--require-tests` | 已实现/已验证节点声明了测试 |
| `--require-symbols` | 指定文件中的真实 Java 声明存在且无歧义 |
| `--require-evidence` | 声明测试有本次通过报告，并匹配当前输入 |
| `--require-ready` | 完整、符号及证据合格，且全部为 VERIFIED |

Demo 的当前状态与未实现项见 [集成说明](../../demo/INTEGRATION.md)；只要仍有 PLANNED，发布就绪检查就应失败。不要批量改状态来消除失败。摘要是漂移检查，不是安全签名，也不证明断言一定充分。Java semanticDigest 与 Binding 设计摘要不同，详见 [Binding 契约](../../skills/dec-implementation/references/implementation-binding.md)。

## 7. 检查派生产物有没有漂移

使用临时目录重生成，而不是修改受管文件来对齐：

```sh
dec_doc_check_dir=$(mktemp -d)
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o "$dec_doc_check_dir/xml" --emit-id-comments
python3 skills/dec-design/scripts/render_dec_graph.py demo/order-payment-yaml \
  -o "$dec_doc_check_dir/html"
diff -qr --exclude=.DS_Store demo/order-payment-xml "$dec_doc_check_dir/xml"
diff -qr --exclude=.DS_Store demo/order-payment-app/src/main/resources/dec/design "$dec_doc_check_dir/xml"
diff -qr --exclude=.DS_Store demo/design-html "$dec_doc_check_dir/html"
```

Java 的生成快照由生成器测试检查；应用中的手写文件不能简单与纯生成输出目录全量比较后删除。

Skill 文件变动后检查包清单，具体参数可用：

```sh
python3 skills/dec-design/scripts/check_manifest.py --help
python3 skills/dec-implementation/scripts/check_manifest.py
```

`dec-design` 与 `dec-implementation` 各自维护独立的源码清单；修改对应 Skill 后先运行相应的校验脚本，再运行该 Skill 的 Python 测试。

## 8. 一次设计变更什么时候算完成

1. canonical 设计明确、ID 稳定、完整目录校验通过。
2. XML 导出、应用镜像、HTML、Java 受管产物与设计一致。
3. 手写适配已适应契约变化；没有复制新的业务判断。
4. 相关成功、失败和边界测试实际通过，数据库路径说明是否运行。
5. Binding 符号和证据对应当前版本；未实现内容保持真实状态。
6. 应用使用新打包设计重建 Runtime；不是只刷新浏览器里的 HTML。

## 9. 出问题时先查哪里

| 问题 | 首先检查 |
| --- | --- |
| HTML 和代码含义不一致 | 是否只改了派生产物、是否重生成完整站点及应用镜像 |
| 修改 XML 后 YAML 没变化 | 同步不是自动触发；是否执行了写回而非仅 `--check` |
| 生成后编译失败 | 本地框架是否 install；手写 ServiceImpl 是否匹配新接口 |
| Rule 返回 UNRESOLVED/ERROR | 模型装配、RuleView 注册、所需关系和数据源是否齐全 |
| Action/监听器未执行 | Business ID/Action 名是否精确注册、路径是否实际进入该动作 |
| 数据未提交或结果未知 | 事务模式、资源所有者、完成回调和数据库提交异常 |
| 查询少数据/报候选上限 | 当前事实是否成立、关系是否装配、是否达到有界候选扫描限制 |
| Binding 证据失效 | 设计、源码、配置、报告或依赖是否变化，是否需要重新运行测试 |

框架本身已有详细专题文档和场景测试；继续定位可从 [框架 README](../../dec-java-lite/README.md) 进入。
