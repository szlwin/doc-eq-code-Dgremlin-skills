# DEC Canonical YAML：Config / DataSource / Connection

仅在编写或修改 `kind: config` 时读取。

## 1. Config

```yaml
kind: config
version: dec/v1

dataSourceInfo:
  default: data1
  dataSources:
    - name: data1
      type: MySQL
    - name: redis1
      type: Redis
    - name: mongo1
      type: MongoDB
    - name: paymentService
      type: HTTP

connectionInfo:
  default: con1
  connections:
    - name: con1
      dataSources: [data1]

dataFiles: [{path: classpath:dec/data/}]
viewFiles: [{path: classpath:dec/view/}]
ruleFiles: [{path: classpath:dec/rule/}]
apiFiles: [{path: classpath:dec/api/}]
enumFiles: [{path: classpath:dec/enum/}]
systemFiles: [{path: classpath:dec/system/}]
businessFiles: [{path: classpath:dec/business/}]
```

## 2. DataSource

`name` 与 `type` 必填；可选 `driverClass/url/username/password`。`name` 是 RuleView/Rule/Data Table 引用的逻辑实例名；`type` 是 DataSource Adapter 类型。

DataSource 统一抽象任何可读写外部能力，包括：RMDB、Redis、MongoDB、MQ、文件、第三方系统、数据中间件、HTTP/gRPC 微服务。上层 Rule 不应根据底层技术重新发明业务模型。

## 3. RuleView / Rule 与 DataSource

RuleView 可配置默认：

```yaml
dataSource: data1
```

单 Rule 可覆盖：

```yaml
dataSource: redis1
```

两者都引用本 Config 中 `dataSources[].name`。

## 4. Connection

Connection 表达同一/多个 DataSource 的具体读写连接方式。`name + dataSources` 必填，`properties` 可扩展。Connection 不是 Business System。

## 5. 文件入口

`dataFiles/viewFiles/ruleFiles/apiFiles/enumFiles/systemFiles/businessFiles` 分别装配对应生成 XML。Enum grammar 见 `enum.md`。

新 DataSource 类型的具体代码由实现层完成；设计层只声明逻辑 type/instance，不写实现 class。
