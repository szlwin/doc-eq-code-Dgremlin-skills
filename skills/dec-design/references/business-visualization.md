# DEC Directory / Information Tree 可视化

本文件只描述 Business Directory Map 与 Information dependency graph 的图形语义。完整 HTML 站点、跨文档链接与覆盖规则见 `design-html-generation.md`。

## 1. Business Directory Map

- Directory：浅绿色矩形；
- Action：浅黄色圆形；
- Dependency：浅蓝色圆角矩形，避免与普通白色详情卡混淆；
- Change：带目标 `informationRef`（或 `process` 标识）的六边形，必须作为实际可点击/拖动的 SVG 节点输出，不能只画箭头；详情展示 Information 定义、表达式/`changeData` 与跨页链接，或完整 process；
- Produce：双边框节点；
- Back：反向虚线。

支持：

- 鼠标滚轮或 `＋/－` 放大缩小；
- 拖动画布空白区域平移；
- 拖动任意节点调整布局；
- 节点移动时关联边自动重算；
- `⟳` 重置视图与节点位置；
- 点击节点在右侧查看结构化详情与跨文档链接。

Dependency 边的方向固定为：

```text
Directory -> prerequisite Information
```

`role: case` 的目标 Directory 已由 case `informationRef` 负责分支判定，因此不得同时声明/绘制 Dependency；validator 与 HTML generator 都应拒绝这种冲突设计。

Directory Inspector 必须能跳转到对应的 Information、View、System、RuleView；Action/Produce/Change/Dependency 的引用也必须可跳转。

## 2. Information dependency graph

Information 按 System 分页，一个 System 一个 HTML；跨 System 依赖作为 external 节点保留。

依赖箭头固定为：

```text
依赖者 → 被依赖者
```

例如：

```text
user.effective → user.activated
user.effective → user.certified
```

而不是反方向。

同一连通依赖组单独布局，组合节点在左、依赖节点在右，并按子节点位置居中；不要把互不关联的信息混排为同一树。当前页面中没有组合引用边的节点单独排列，并注明这是当前图的范围，不能推断它们在其他 System 中也未被引用。

图上必须区分简单平铺 expression 的 `OR`（任一）、`AND`（全部）和 `NOT`（取反）；例如 `order.payable` 的两条引用边是 OR，不能只显示通用 `depends on` 让读者误解为 AND。带括号、混合运算或同一引用既正向又取反时，不简化成单一操作符，使用“表达式引用”，并在页面直接展示完整 canonical expression。关系表示条件依赖，不是业务执行顺序。

节点标题和中文说明按显示宽度换行；过长说明截断时标记省略号，完整内容仍在 tooltip / Inspector 中可读。共享依赖只保留一个节点；循环依赖报错，禁止布局陷入无限循环。

Information 节点上、下、左、右各显示一个可聚焦的“＋”；点击后在对应方向打开独立的紫色规则详情框，并用紫色虚线连接原节点。按钮变为“−”，再次点击只收起该方向的框；四个方向可分别打开，Enter/空格等效。原节点保持大小，点击节点本身不再向下展开。紫色连线仅表示详情归属，不代表 Information 依赖或业务执行边。`ruleData`、`changeData`、组合 expression 直接展示；`ruleRef` 应解析并内嵌完整 RuleView 与各 Rule 定义、有效 DataSource，不要求读者再点击右侧链接。未知 RuleView 明示缺失，不用空白或假定义代替。长内容在展开框内独立滚动并允许选择复制；滚动、选中文本不触发图缩放、拖动或收起。

新详情框沿选定方向避开已有节点，打开后视口包含原框和详情框。原节点和详情框均可拖动，连线跟随两端；长规则可在框内独立滚动。重置按钮关闭所有详情框、恢复“＋”与原始位置和视口。展开仅影响浏览器展示状态，不改变 canonical YAML。下方完整定义卡保留交叉链接。

Information 的完整定义使用分组式展示，并至少包含：

- Design ID / Information Key；
- System；
- description；
- recognizer 类型；
- View（如有，可跳转）；
- RuleView（如有，可跳转）；
- Dependencies（可跳转）；
- ruleData / expression / changeData。

## 3. 设计事实边界

- 图形只来自 canonical YAML；
- HTML 只能由 `scripts/render_dec_graph.py` 生成；
- YAML 修改后重新生成并覆盖旧 HTML；
- 禁止手工维护 HTML；
- 图中拖动只影响浏览器临时布局，不回写 YAML；
- 设计是否合法仍由 `validate_dec_yaml.py` 判定。

Information 图保留右侧详情面板：点击原信息框显示完整定义与 System / View / RuleView 等跳转链接；四向“＋/−”独立控制图内规则框，不替代或清空右侧面板。窄屏下详情面板沿用响应式布局放到图下方。

每个紫色规则框也必须绑定自己的右侧详情模板；点击框的标题或边框后，右侧展示该框所属 Information 与规则定义，不能停留在上次选中的节点。

图内展开框和右侧详情共用“识别规则”展示：明确标注规则来源，内联 ruleData 展示完整条件，ruleRef 展示引用 RuleView 的完整规则，changeData 单独展示物化内容。原节点与其四向规则框的右侧详情一致，不重复显示规则，也不能为统一外观替换 canonical 识别规则。

Directory 流程图的 Dependency Information 支持逐级展开：点击节点默认向右展开，也可使用四向“＋”选择方向。组合 Information 先展开直接引用的 Information，连线保留 OR/AND/NOT 或“表达式引用”语义；原子 Information 再展开紫色规则详情框。例如 `order.payable → ordered / waitPay → 各自规则`。展开出的 Information 也带四向按钮；每个节点同时使用一个展开方向，切换方向会重新定位子框。同一 Dependency 入口内的共享引用复用一个节点，不重复生成。收起时隐藏不可再达的后代并重置其按钮；仍由其他已展开分支引用的节点保留。重置关闭全部展开内容。节点点击同步右侧完整详情，方向按钮保留当前右侧菜单；规则文本支持独立滚动、复制。
