# DEC Directory / Information Tree 可视化

本文件只描述 Business Directory Map 与 Information dependency graph 的图形语义。完整 HTML 站点、跨文档链接与覆盖规则见 `design-html-generation.md`。

## 1. Business Directory Map

- Directory：浅绿色矩形；
- Action：浅黄色圆形；
- Dependency：浅蓝色圆角矩形，避免与普通白色详情卡混淆；
- Change：六边形；
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

Information Inspector 使用分组式展示，并至少包含：

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
