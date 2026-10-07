# DEC HTML 设计文档生成

本文件定义 DEC canonical YAML → HTML 设计文档的生成规则。

## 1. 强制规则

所有 DEC HTML 设计文档都必须通过 Skill 内置脚本生成：

```bash
python3 scripts/render_dec_graph.py <yaml-file-or-dir> -o <business.html>
```

禁止手工创建或维护 DEC HTML 设计事实。YAML 发生修改后，必须重新运行脚本；脚本默认覆盖同名已生成 HTML，这是预期行为。

权威关系始终是：

```text
Canonical YAML = Source of Truth
Generated HTML = Derived documentation / review artifact
```

HTML 不回写 YAML，也不参与设计合法性判断；合法性仍由 `validate_dec_yaml.py` 负责。

## 2. 一次生成的文档

例如：

```bash
python3 scripts/render_dec_graph.py examples -o generated-html/order-payment.html
```

会在同一目录生成：

```text
order-payment.html                         # Directory 首页 / Business Directory Map 列表
order-payment-index.html                   # 设计文档总览
order-payment-datasources.html             # DataSource / Connection 全局索引
order-payment-data.html                    # Data 全局稳定跳转目标
order-payment-views.html                   # View 全局稳定跳转目标
order-payment-ruleviews.html               # RuleView 全局稳定跳转目标
order-payment-apis.html                    # API 全局稳定跳转目标
order-payment-systems.html                 # System 总入口
order-payment-enums.html                   # Enum 全局稳定跳转目标
directory/<business>.html                  # 每个 Business Directory Map 详细页
system/<system>/index.html                 # System 首页
system/<system>/data.html                  # System Data
system/<system>/views.html                 # System View
system/<system>/ruleviews.html             # System RuleView
system/<system>/apis.html                  # System API
system/<system>/enums.html                 # System Enum
system/<system>/information.html           # System Information dependency graph
```

如果原 HTML 已存在，脚本直接覆盖，避免 HTML 与 YAML 漂移。

### 2.1 System-first 主导航

View、RuleView、API、Enum、Information 的首要人工导航入口必须是 System：

```text
Overview
  -> System index
      -> user / order / payment / ...
          -> Views
              -> RuleViews
          -> Enums
          -> APIs
          -> Information
```

每个 `View.system` 定义 View ownership。System 也可以通过 `viewRefs` 引用其他 System 拥有的 View；这种 View 在 System 页面标记为 `referenced · owner=<system>`，其下只展示与当前 System 有关联的 RuleView。

全局 View/RuleView/API/Enum HTML 仍会生成，作为稳定跨页链接与检索目标，但不作为顶部主导航入口。

## 3. 稳定跳转关系

脚本为主要设计节点生成稳定锚点，并把引用字段转换为相对链接：

- Data.table.dataSource → DataSource；
- Data.column.relEnum → Enum；
- View.system → System；
- View.targetMain / Relation.data → Data；
- RuleView.viewRef → View；
- RuleView.apiRef → API；
- RuleView/Rule.dataSource → DataSource；
- API.system → System；
- API.response.modelRef → View；
- API.relEnum → Enum；
- System.dataRefs → Data；
- System.viewRefs / ModelAccess.modelRef → View；
- System Information → 对应 Information HTML；
- Information.viewRef → View；
- Information.ruleRef → RuleView；
- Information dependency → Information；
- Directory.informationRef / Dependency / Change / Produce.informationRef → Information；
- Directory.modelRef → View；
- Action.systemRef → System；
- Action.ruleRef → RuleView；
- Directory relation → Directory。

因此 HTML 是一个可导航的设计文档网络，而不是互不关联的静态页面。

## 4. Information dependency 方向

Information 图的箭头必须表达“谁依赖谁”：

```text
依赖者  ── depends on ──▶  被依赖者
```

例如：

```text
user.effective ─────▶ user.activated
               └────▶ user.certified

order.payable ──────▶ order.ordered
              └─────▶ order.waitPay
```

这与 expression 的语义一致；不得画成反方向。

跨 System 依赖仍保留在当前 System 页面中，并以 external 样式显示。

## 5. Business Directory Map

Business 页面保持业务阅读优先：

- Directory：绿色矩形；
- Action：黄色圆形；
- Dependency：浅蓝色圆角框；其箭头固定为 `Directory -> prerequisite Information`；
- Produce：双边框节点；
- Change：六边形；
- Back：反向虚线；
- `role: case` 使用 case 分支边；case 目标 Directory 不再重复绘制 Dependency gate。

支持滚轮/按钮缩放、画布平移、节点拖动、连线跟随和视图重置。

点击 Directory、Action、Dependency、Produce、Change 后，右侧 Inspector 使用分组式展示，并把 `informationRef/modelRef/systemRef/ruleRef` 转成可点击链接。

## 6. Information 页面

每个 System 单独生成一份 Information HTML：

- 本 System 节点为主样式；
- 外部依赖以灰色虚线节点显示；
- 支持缩放、平移、节点拖动；
- 点击节点后，右侧按 `基本信息 / Dependencies / Rule data / Expression / Change data` 分组展示；
- `System / View / RuleView / dependency Information` 均可直接跳转。

Information 建议提供 `description`，用于 HTML 中快速理解节点；缺少 description 不影响 grammar，但会降低 Review 可读性。

## 7. Enum / Information change 可视化

Enum 页面必须展示每个 value 的具体值、名称和说明；若 Information `changeData` 写入 enum-bound View property，还必须生成 `Information change mappings`，把以下链路连起来：

```text
Information -> changed View property -> Enum -> concrete value/name
```

System 页面中的 Enum 列表同样展示具体 `value=name`，点击 Enum 可进入完整映射页。

## 8. 生成后检查

至少执行：

```bash
python3 scripts/validate_dec_yaml.py <yaml-dir>
python3 scripts/render_dec_graph.py <yaml-dir> -o <generated-html/business.html>
```

如果 XML 也需要同步：

```bash
python3 scripts/yaml_to_xml.py <yaml-dir> -o <generated-xml-dir>
```

不得通过直接修改 HTML 来“修复”设计文档；应该修改 canonical YAML 或生成脚本，再重新生成并覆盖 HTML。


## System 目录布局

所有 System 必须统一放在站点根目录的 `system/` 下，避免 System 名称与 `directory/`、`system/` 等保留目录冲突。每个 System（包括 `common`）生成独立子目录：

```text
system/
  <system>/
    index.html
    data.html
    views.html
    ruleviews.html
    apis.html
    enums.html
    information.html
```

例如：

```text
system/
  user/
  order/
  payment/
  common/
```

禁止在站点根目录直接生成 `<system>/`。这样即使业务 System 名称为 `directory`，其路径也是 `system/directory/`，不会与 Business Directory Map 的 `directory/` 冲突。

System 首页必须列出 Data、View→RuleView、Enum、API、Information，点击后进入本 System 目录下对应详情页。

Directory 首页必须列出 Business Directory Map；每个 Business Map 生成到 `directory/<business>.html`。

View 详情必须以层级方式展示 Relation/子对象；标量 Property 显示 Data-derived logical type、`desc`、Enum 链接。
