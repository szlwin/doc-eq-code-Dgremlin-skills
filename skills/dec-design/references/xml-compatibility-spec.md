# DEC Canonical YAML → XML 映射规范索引

仅在生成、Review 或排查 XML 时读取。普通 YAML 建模无需加载 XML 映射。

1. 先读 `xml-mapping/common.md`。
2. 再只读当前 kind 分片。

| kind | XML 映射 |
|---|---|
| `config` | `xml-mapping/config.md` |
| `data` | `xml-mapping/data.md` |
| `view` | `xml-mapping/view.md` |
| `rule` | `xml-mapping/rule.md` |
| `api` | `xml-mapping/api.md` |
| `enum` | `xml-mapping/enum.md` |
| `systems` | `xml-mapping/system-information.md` |
| `business` | `xml-mapping/business.md` |

Canonical YAML 是 Source of Truth；XML 是派生产物。已有 XML 反向迁移先读 `xml-to-yaml.md`，转换后立即运行 validator 并回到 YAML 工作流。
