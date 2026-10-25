# 第六篇 CSDN 发布准备

[仓库阅读版](../doc-eq-code-6-ai.md)是维护源；[CSDN 发布稿](doc-eq-code-6-ai-csdn.md)由它派生。后续修改正文时，应同步重新导出发布稿。

本稿于 2026-10-25 根据 `ec4d23ec80d024943d99e4658577c631f763cde7` 的源码基线整理。已将 32 处仓库文档、源码和目录链接改为固定版本的 GitHub 地址，保留代码示例中的仓库相对路径。

## 发布前处理

1. 将发布稿导入 CSDN Markdown 编辑器。
2. 上传下列三张图片，把正文中对应的本地图片地址替换为 CSDN 返回的图片地址。当前图片链接仅供本地预览，不是已上传地址。
3. 在 CSDN 预览中检查流程图、表格、代码块和图片。若编辑器不支持 Mermaid，可在发布时将该图转为图片。
4. GitHub 的 HTML 链接用于查看源码；文章已说明下载或克隆仓库后，在本地打开完整 `demo/design-html` 站点。当前没有提供已部署的公开交互地址。

| 图片 | 用途 |
| --- | --- |
| [dec-directory-demo.png](../assets/dec-directory-demo.png) | 订单支付流程总览 |
| [dec-paying-detail.jpg](../assets/dec-paying-detail.jpg) | paying 放大局部与节点详情 |
| [dec-information-demo.png](../assets/dec-information-demo.png) | 订单信息依赖图 |

本目录只提供发布准备文件，未上传图片或发布文章。
