# 修复复核：2026-10-26

范围：复核 [2026-10-25 检查报告](health-check-2026-10-25.md)的 F1–F6，检查当前未提交修复及新增测试。基线提交仍为 `75412f9`，本次以工作区实际内容为验证对象。未修改现有实现或用户修复；只新增本报告，并重新生成忽略目录内的构建与测试证据。

## 结论

原六项问题在本次 macOS / Java 21 / MySQL 8.4 验证范围内通过复核。修复包含针对性的回归测试，未发现原问题在这些用例中继续出现。

另发现一项由生成器修复引入的 Windows 路径兼容性问题，见 R1。因此本次结论是：原问题修复有效，但仍有一项跨平台回归需要处理。

## 新发现

### R1 · P2：manifest 的规范路径比较会阻止 Windows 重生成

位置：[GeneratedProjectWriter.java](../../../dec-java-lite/dec-lite-generator/src/main/java/com/dec/lite/generator/GeneratedProjectWriter.java)，`ownedFiles` 第 99–100 行。

新增检查使用：

```java
if (!target.relativize(file).toString().equals(relative))
    throw new GenerationException("non-canonical generated manifest entry: " + relative);
```

manifest 由生成器使用 `/` 保存，例如 `src/main/java/example/App.java`；Windows 默认文件系统的 `Path.toString()` 使用反斜杠，得到 `src\main\java\example\App.java`。二者不相等，正常路径会被认定为不规范路径。

影响：Windows 上首次生成可以成功，但已有生成目录的下一次生成会在读取 manifest 时失败；macOS/Linux 上的现有测试无法发现该问题。这是源码可确定的路径表示差异，本次未运行 Windows 实机测试。

建议：比较前将相对路径转换为 manifest 统一使用的 `/` 表示，或以路径分量验证规范性；保留对绝对路径、越界、符号链接和不规范条目的拒绝。添加“首次生成后再次生成”的 Windows 验证。

## 原六项复核

| 问题 | 结论 | 本次依据 |
| --- | --- | --- |
| F1 手写文件覆盖 | 原问题已修复；另见 R1 | 先暂存、校验完整写入集合；测试覆盖 Service/Controller 同名冲突、晚期模板失败、部分提交失败恢复；生成器测试通过 |
| F2 字符串下推漏查 | 通过 | 使用 Runtime AST 的字面量；空格、制表符、换行、逻辑关键字及转义相关测试通过；真实 MySQL 语义回归通过 |
| F3 基础设施故障返回 409 | 通过 | 支付 DAO 故障返回 503，原始 cause 保留；部分写入失败整体回滚；原业务冲突、监听器失败与替代 Adapter 失败测试通过 |
| F4 Skill 清单失效 | 通过 | 两个清单均校验通过；implementation 清单仅更新原报告指出的摘要 |
| F5 CI 就绪门禁不足 | 通过 | CI 最终调用已改为 `--require-ready`；本次新证据生成后就绪校验通过 |
| F6 发布文档数量过期 | 通过 | 九模块明确限定为历史验收快照；当前模块及 Demo 状态改为引用事实来源，不再重复写旧数量 |

## 本次独立运行结果

| 验证 | 结果 |
| --- | --- |
| 框架 `install -Prelease-artifacts,mysql-it` | 207 项，0 失败、0 错误、0 跳过；sources/Javadoc 构建成功 |
| Demo 经 Binding 包装器执行 `clean test` | 44 项，0 失败、0 错误、0 跳过 |
| implementation Python 测试 | 32 项通过 |
| Skill 文件清单 | design 100 个文件、implementation 33 个文件，均通过 |
| canonical 校验 | 10 份文件、118 个 Design ID，通过 |
| Binding `--require-ready` | 118/118；完整覆盖、符号、本次证据、就绪状态均为 true |
| XML/HTML 重生成比对 | 导出 XML、应用内 XML、38 页 HTML 均无漂移 |
| `git diff --check` | 通过 |

使用新建的独立 MySQL 8.4 容器及专用测试库，未连接项目默认数据库；测试结束后容器已停止并自动删除。未执行远程 GitHub CI、Windows 实机测试、Agent 级 Skill 模型评测或真实第三方支付/短信接入验证。

原报告提出的 Skill 模型评测、生成工程启动接线测试等长期建议不属于本次六项缺陷的修复验收，仍保留为后续建设项。
