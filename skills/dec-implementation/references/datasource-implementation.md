# DataSource 实现规范

DEC DataSource 是统一外部能力边界：RDBMS、Redis、MongoDB、MQ、文件、第三方系统、数据中间件、HTTP/gRPC 微服务都通过同一抽象接入。

## 1. RuleView / Rule 使用方式

RuleView `dataSource` 是默认值；Rule `dataSource` 是局部覆盖。实现必须把逻辑 DataSource name 解析到 Config 中声明的实例，不得根据名称猜类型。

示例：

```text
RuleView default = mysqlOrder
Rule A no override → mysqlOrder
Rule B dataSource = redisOrder → redisOrder
Rule C dataSource = paymentService → HTTP/gRPC adapter
```

## 2. 新 DataSource type

通常实现：

```text
DataSource
DataConnection
ConvertContainer
DataConvertContainer
ExecuteContainer
+ factories / registration
```

- `DataSource`：物理能力代理；
- `DataConnection`：连接/请求/事务能力；
- `ConvertContainer`：DEC command/mapping → 物理命令；
- `DataConvertContainer`：逻辑/物理类型转换；
- `ExecuteContainer`：执行请求。

## 3. Binding / Testing

新增 type 时 Binding 至少覆盖 adapter、factory/registration 与契约/集成测试。第三方系统或微服务要测试超时、错误映射和返回数据转换，但这些技术策略不能偷偷改变业务 Rule。

## 4. 结构化数据边界

如果 DataSource 返回 Object/List/JSON，而 canonical grammar 尚未批准对应结构，不在实现层擅自加 YAML 字段或长期塞 JSON 字符串；返回 `DESIGN_GAP`。
