# 生成器文件所有权与迁移契约

`generate` 只管理上一轮 `dec-generated-manifest.txt` 中列出的文件。路径统一为 `/` 分隔的规范相对路径，包括 Windows。`.dec-generated` 和 manifest 是生成器元数据，不能由应用代码复用。

| 情况 | 行为 |
|---|---|
| 输出目录不存在或为空 | 可生成新工程 |
| 非空目录缺少 marker，或 marker/manifest 不合法 | 拒绝，保留原目录 |
| 新文件名已经被不在旧 manifest 内的手写文件占用 | 拒绝，即使内容恰好相同也不取得所有权 |
| 旧 manifest 内的文件仍在本次输出中 | 允许覆盖，包括对生成文件的手工修改；手写代码必须放在独立文件 |
| 旧受管文件不再生成 | 删除；不在 manifest 内的文件保留 |
| 符号链接、越界路径、文件/目录冲突 | 拒绝 |
| 模板渲染失败 | 临时区失败，目标不变 |
| 写入阶段出现可捕获异常 | 恢复旧文件字节、清理新文件和新建目录；恢复失败附在 suppressed exception 中 |

这不是文件系统事务：不保证进程崩溃、断电、恢复时磁盘不可写或多个进程同时写入时的原子性。调用方应串行生成，并保留版本控制或备份。

## 先预览再生成

```sh
java -jar dec-java-lite/dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate \
  --yaml demo/order-payment-yaml --output /tmp/dec-order-preview \
  --base-package com.example.orderpayment \
  --generation-config demo/order-payment-generation.properties --dry-run
```

`--dry-run` 完整渲染并使用与提交相同的所有权检查，不创建或修改目标目录。按相对路径输出 `CREATE`、`UPDATE`、`DELETE`、`UNCHANGED`、`PRESERVE`、`CONFLICT`；元数据另列说明。存在冲突返回 1，成功返回 0。预览不是锁或授权令牌；正式生成会重新检查。XML 不由 Java 生成器写入，需另外使用 converter 导出并同步到应用资源目录。

## API 分组迁移

1. 保存旧设计/生成配置与手写实现，审阅 dry-run 中删除和新增的接口、Controller。
2. 迁移手写 Service 实现所实现的接口；拆分或合并职责时逐一核对 API Design ID、参数、响应和事务边界。生成器保留手写文件，不会自动迁移业务代码。
3. 删除不再使用的应用 Bean、配置和旧实现，更新 Binding 的真实文件与符号。仅生成成功不能证明迁移完成。
4. `clean` 后编译、启动 Spring，检查旧 Bean/重复 endpoint 不存在，并通过新 Controller 调用到实际 Service、Runtime 和持久化。
5. 同步 XML，验证应用 JAR 内的资源清单、文件与当前设计一致；重建 Binding 验证证据。

自动化覆盖：`GeneratedProjectWriterTest` 验证保护与恢复，`GenerationOptionsTest` 验证分组和预览，`MainTest` 验证 CLI 退出码；`tools/verify_generated_projects.py` 在费用领域执行默认分组→Approvals 分组迁移，验证旧类/Bean 清理、真实 Spring 接线、MySQL 提交/回滚和 JAR 中的 XML。它提供框架契约回归，不替代任意应用手写 Service 的迁移审查。
