# 当前 DEC 兼容范围

本 Skill 以当前 DEC/P3 Runtime 兼容语义为基线；所有可执行约束以本包的 references、schemas、templates 和 converter 实现为准。

## Canonical YAML 支持

- Config：DataSource、Connection、Data/View/Rule/API/Enum/System/Business 文件入口；
- Enum：公共业务枚举及 value/name/desc；
- Data：property + 当前 external mapping + Column `relEnum`；
- View：业务属性、必填 `system` ownership、one-to-one / one-to-many；
- RuleView：`name/code/desc/viewRef/apiRef/dataSource`；Rule 可单独 `dataSource` 覆盖；
- API：URL/method/request/required/length/enum/relEnum/expression/response；
- System / Information / ModelAccess；
- Business：desc + P3 Directory/Dependency/Action/Produce/Change/Back。

## XML 目标扩展

- RuleView/Rule 的 DataSource 属性按当前规范输出 `dataSource`；
- RuleView `code/desc` 输出同名属性；
- Enum 输出 `<enum-config>/<enum>/<enum-value>`；Config `enumFiles` 输出 `enum-file-info/enum-file`；
- Data Column、API Parameter/Field 的 `relEnum` 输出 `rel-enum`；
- API request expression 输出 `<validation type="expression" expression="...">`。

这些新增 XML grammar 需要对应 Compiler/Runtime 支持。Converter 能生成 XML **不等于** Runtime 已实现；未实现时必须标记 capability gap。

## 历史兼容

- canonical `dsl` → XML `grammer`；canonical `cmd` → XML `sql`；
- Data 根输出 `<orm-data-mapping>`；旧 `<orm--data-mapping>` 仅导入兼容；
- 旧顶层 `directories:` / XML `directory-config` 永久排除；
- 未批准的结构化 Data grammar 不擅自新增。


## 可视化派生产物

`scripts/render_dec_graph.py` 将加载到的 canonical DEC YAML 渲染为自包含、互相链接的 HTML 设计站点：DataSource、Data、View、RuleView、API、System、Enum、Business Directory、每个 System 的聚合设计页，以及按 System 拆分的 Information dependency 页面。View/RuleView/API/Enum/Information 以 System 为首要导航入口；Business depend 使用 `Directory -> prerequisite Information`，Information dependency 使用“依赖者 → 被依赖者”。Business/Information 图支持缩放、平移和节点拖动。HTML 只允许由脚本生成并可覆盖旧文件，不参与 Runtime，也不是设计事实源。
