# 输入与产物边界

在进入设计流程前，先判断输入属于哪一层。文件扩展名不能代表它是 canonical DEC YAML。

## 1. 四类输入

| 输入 | 识别特征 | 用途 | 处理方式 |
|---|---|---|---|
| canonical DEC YAML | 有 `kind: config|data|view|rule|api|enum|systems|business` | 唯一设计事实源 | 用 `yaml_io.py` 读取，`validate_dec_yaml.py` 校验，再生成 XML/HTML |
| legacy runtime YAML | 常见根键为 `data`、`view`、`datasources`、`directories`、`businesses`，通常没有 `kind` | 兼容性、迁移和行为参考 | 先标记为 legacy；逐字段映射到 canonical YAML，禁止与 canonical 文件混目录直接转换 |
| DEC XML | 根元素如 `orm-config`、`orm-data-mapping`、`orm-view-mapping`、`orm-rule-mapping`、`systems`、`business-config` | 首次迁移或外部编辑回流 | 首次迁移走 `xml_to_yaml.py`；已有 YAML 的外部修改走 `sync_xml_to_yaml.py` |
| HTML recovery artifact | `artifact.producer: html-recovery`、`sourceText`、`source_rows`、`informationTree` 等 | 展示、导航和人工核对 | 不得校验、转换或作为实现输入；回到原始 YAML/XML/需求重新建模 |

## 2. legacy 与独立声明语言

legacy runtime YAML 可能把配置、数据、视图、规则、目录或声明业务放在不同的根结构中；这些结构的解析器约定与 canonical DEC YAML 不同。需要迁移时：

- 先记录来源格式、版本和加载器；
- 逐字段映射到一个明确的 canonical `kind`，无法表达的语义登记 `DESIGN_GAP`；
- 独立的数据声明语言保持独立，不把其 `systems/businesses/depends` 等结构伪装成 DEC Data/View/Rule/Business；
- 不为了通过 validator 而静默删除字段、猜测引用或把实现类名写入设计文档。

## 3. HTML recovery artifact

HTML 反向抽取工具可能把页面标题、表格行、导航链接和原始文本保存为 YAML。此类文件仍是展示索引：

- `sourceText`、`source_rows`、`informationTree` 等字段不是 DEC 语义结构；
- 不能直接送入 `validate_dec_yaml.py`、`yaml_to_xml.py` 或实现绑定校验；
- 若没有原始 canonical YAML/XML，必须重新根据需求建模，并把抽取文件仅作为人工核对材料。

## 4. 最小预检

```sh
python3 scripts/yaml_io.py read <input>
python3 scripts/validate_dec_yaml.py <canonical-yaml-dir>
python3 scripts/yaml_to_xml.py <canonical-yaml-dir> --check
```

如果第一步输出的是 `artifact/sourceText/source_rows`，停止转换并报告“展示工件，不是设计源”。
