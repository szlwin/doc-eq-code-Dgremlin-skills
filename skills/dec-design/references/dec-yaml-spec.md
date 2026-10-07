# DEC Canonical YAML 规范索引

本文件只负责路由。先判断当前 kind，再按需读取；不要一次加载全部规范。

## 必读顺序

1. 首次进入任务：`source-basis.md`。
2. 任意 YAML 写入：`yaml-spec/common.md`。
3. 当前 kind 对应分片。
4. 跨两个及以上 kind：追加 `yaml-spec/cross-file.md`。
5. 从 `templates/<kind>.yaml` 开始编写。

## kind → 分片

| kind | 规范 | 条件追加 |
|---|---|---|
| `config` | `yaml-spec/config.md` | 装配其他 kind 时读 `cross-file.md` |
| `data` | `yaml-spec/data.md` | `relEnum` 时读 `enum.md` |
| `view` | `yaml-spec/view.md` | 修改 relation 时确认 `data.md` |
| `rule` | `yaml-spec/rule.md` | `apiRef` 读 `api.md`；DataSource 读 `config.md` |
| `api` | `yaml-spec/api.md` | `relEnum` 读 `enum.md`；绑定 RuleView 时读 `rule.md` |
| `enum` | `yaml-spec/enum.md` | 引用影响分析读 `cross-file.md` |
| `systems` | `yaml-spec/system-information.md` | 按引用补读 Rule/View |
| `business` | `yaml-spec/business.md` | 修改 Information 时读 `system-information.md` |

## 按问题读取

- DataSource / Connection：`config.md`
- Data / Column / `relEnum`：`data.md` + `enum.md`
- RuleView / Rule / `dataSource` / `code`：`rule.md`
- API required/length/enum/expression：`api.md`
- 公共枚举：`enum.md`
- System / Information / ModelAccess：`system-information.md`
- BusinessScope / Directory / Action / Produce / Change / Back：`business.md`
- 跨文件引用：`cross-file.md`

validator/schema 与规范不一致时登记 `DESIGN_GAP`，不得静默绕过。
