# DEC 设计 Review 检查表

## 文档与规范

- 是否读取 `source-basis.md`、`dec-yaml-spec.md` 索引、`yaml-spec/common.md` 与当前 kind 分片；
- 是否使用对应 kind 的模板；
- 是否误用了明确排除的 declaration 文档；
- 是否出现规范未定义字段；
- 是否仍存在旧 `directories:` / `directory-config`。

## DataSource / Enum / Data

- Enum name 是否唯一；
- Enum value/name 在枚举内部是否唯一；
- `relEnum` 是否引用真实 Enum；
- 同一 API 字段绑定 `relEnum` 后是否避免再维护另一份 inline enum values；


- DataSource 是否复用统一抽象；
- Connection 与 DataSource 引用是否存在；
- Data property 是否有逻辑类型；
- table `key/keyType/columns` 是否完整；
- column ref 是否指向真实 property；
- column `relEnum` 是否与接口/代码使用同一 Enum 事实；
- 是否把当前不支持的结构化 Data 能力伪装成已支持。

## View

- `targetMain` 是否存在；
- scalar ref 是否存在；
- relation 类型是否仅 one-to-one / one-to-many；
- relation data/key/relKey 是否可解析；
- 业务模型是否避免实现类耦合。

## API

- API 的 name/system/url/method 是否明确；
- path/query/header/body 参数来源是否正确；
- URL path variable 是否与 path 参数一致；
- required/default/validation 是否自洽；
- required、min/max、minLength/maxLength、regex、enum/relEnum 是否完整；
- request-level expression（如 `status = 1 and type = 1`）是否只表达传输契约而非业务规则；
- response type/modelRef/fields 是否明确；
- RuleView.apiRef 是否需要且精确绑定。

## RuleView

- `name/code/desc` 是否明确，`code` 是否全局唯一；
- `viewRef` 是否存在；
- RuleView `dataSource` 默认值是否正确；
- Rule `dataSource` override 是否只在确有不同存储/第三方/微服务时使用；
- Rule 顺序是否有业务含义并正确；
- rule type 是否符合规范；
- `dsl` 是否使用 `process`；
- 数据源命令是否使用 canonical `cmd`；
- error code/message 是否完整。

## System / Information

- System Owner 是否唯一；
- Data/View/RuleView 是否属于正确 System；
- Information local name 是否唯一；
- Information 是否恰好一个 recognizer：ruleRef/ruleData/expression；
- atomic Information 是否有 viewRef；
- composite 是否没有 viewRef/changeData；
- expression 是否只组合 Information；
- Information graph 是否无环；
- RuleView view 与 Information view 是否兼容；
- ModelAccess 是否 exact，不做 fuzzy fallback。

## Business / Directory

- 至少一个 root；
- Directory `informationRef/modelRef` 是否存在；
- subDirectory relation 是否明确且符合当前 P3方向；
- Dependency 是否只引用 Information；
- RuleView Action 是否有 systemRef + ruleRef；
- Custom Action 是否没有 ruleRef 且后续必须有实现 binding；
- Action 顺序是否正确；
- Produce 数据后置条件是否明确；
- 作为后续信息的 Produce 是否有 informationRef；
- Produce 是否错误映射到 composite Information；
- Change 是否指向可物化事实；
- Back 是否绑定有效关系、逐级补偿；
- result/case 是否能保证需要的分类约束。

## HTML 设计文档

- HTML 是否全部通过 `scripts/render_dec_graph.py` 从 canonical YAML 生成；
- 是否不存在手工维护的 DEC HTML 设计事实；
- YAML 修改后是否重新生成并覆盖旧 HTML；
- DataSource/Data/View/RuleView/API/System/Enum/Information/Directory 是否可通过相对链接互相导航；
- Information dependency 箭头是否为“依赖者 → 被依赖者”；
- Information 右侧详情是否包含 System/View/RuleView/Dependencies 跳转；
- Directory 右侧详情是否包含 Information/View/System/RuleView/Produce/Change/Back 跳转；
- 跨 System Information dependency 是否保留 external context。

## 工具 Gate

```sh
python3 scripts/validate_dec_yaml.py <design-dir>
python3 scripts/yaml_to_xml.py <design-dir> --check
# 需要 HTML 文档时：
python3 scripts/render_dec_graph.py <design-dir> -o <generated-html/business.html>
```

若项目提供 Compiler/Runtime 测试，还必须执行真实项目验证。
