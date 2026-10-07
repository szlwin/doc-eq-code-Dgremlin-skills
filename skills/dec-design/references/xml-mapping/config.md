# DEC YAML → XML：Config

`kind: config` → `<orm-config>`。

DataSource：`dataSourceInfo/dataSources` → `orm-datasource-info/orm-datasource`；`type` 写入子元素 `<name>`，兼容 `driver-class/url/username/password`。

Connection：`connectionInfo/connections` → `orm-connection-info/orm-connection/data-source-info`。

文件入口：

| YAML | XML container | child |
|---|---|---|
| `dataFiles` | `orm-data-file-info` | `orm-file` |
| `relationFiles` | `orm-relation-file-info` | `orm-file` |
| `viewFiles` | `orm-view-file-info` | `orm-file` |
| `ruleFiles` | `orm-rule-file-info` | `orm-file` |
| `serviceFiles` | `orm-service-info` | `orm-file` |
| `apiFiles` | `api-file-info` | `api-file` |
| `enumFiles` | `enum-file-info` | `enum-file` |
| `systemFiles` | `system-file-info` | `system-file` |
| `businessFiles` | `business-file-info` | `business-file` |
