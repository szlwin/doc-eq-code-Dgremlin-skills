# DEC Canonical YAML：Data

仅在编写或修改 `kind: data` 时读取；Column 绑定公共 Enum 时追加读 `enum.md`。

## 1. Data

```yaml
kind: data
version: dec/v1
datas:
  - id: DATA-ORDER
    name: order
    system: order
    properties:
      id: int
      orderStatus: int
      createDate:
        type: date
        desc: 创建时间
    tables:
      - name: order_info
        dataSource: data1
        key: o_id
        keyType: increment
        columns:
          o_id: id
          o_status:
            ref: orderStatus
            relEnum: OrderStatus
          o_date:
            ref: createDate
            type: timestamp
```

## 2. Data / Property

Data：`id` 推荐，`name/system/properties` 必填，`tables` 可选。`system` 是 Data 的归属 System，也是 HTML System → Data 导航的依据。Property 简写 `id: int` 等价于 `{type: int}`；完整字段支持 `type + desc`。

Property `type` 是逻辑类型，不写 Java class。

## 3. Table / External Mapping

| 字段 | 必填 | 说明 |
|---|---:|---|
| `name` | 是 | 外部数据映射名，RMDB 类似表名 |
| `dataSource` | 是 | Config DataSource name |
| `key` | 是 | 数据源侧唯一标识 |
| `keyType` | 是 | `increment|set` |
| `columns` | 是 | 外部字段→Data property |

DataSource 可是 RMDB、Redis、MongoDB、MQ、文件、第三方系统、数据中间件或微服务；`table/column` 是当前 Runtime 的历史兼容命名，不代表只能映射关系型数据库。

## 4. Column

简写：

```yaml
columns:
  o_id: id
```

完整：

```yaml
columns:
  o_status:
    ref: orderStatus
    type: int
    relEnum: OrderStatus
```

| 字段 | 必填 | 说明 |
|---|---:|---|
| `ref` / `refProperty` | 是 | 本 Data property |
| `type` | 否 | 数据源侧类型，用于 DataConvert |
| `relEnum` | 否 | 公共 Enum name，XML 输出 `rel-enum` |

`relEnum` 存在时必须能解析到 `kind: enum` 中的 Enum。

## 5. 当前结构化数据边界

当前历史 Data 模型主要是行列式；未批准前不要擅自新增 `object/array/path` 等 grammar。需要结构化 DataSource 时登记 `DESIGN_GAP` 或使用已批准扩展。
