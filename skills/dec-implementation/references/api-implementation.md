# API 实现契约

API 负责传输层契约，不是业务 Rule 的副本。

## 1. 必须保持的事实

- URL / HTTP method；
- parameter `in/type/required/default`；
- `min/max/minLength/maxLength/pattern/regex`；
- inline `enum` 或公共 `relEnum`；
- request-level `expression`（仅纯传输层组合约束）；
- response type/modelRef/fields/relEnum；
- 所属 System。

## 2. 执行顺序

RuleView 存在 `apiRef` 时：

```text
HTTP endpoint
→ required/type/path binding
→ min/max + length/pattern/regex/enum/relEnum validation
→ request expression validation
→ request mapping
→ DEC RuleView(name)
→ response mapping/enum serialization
```

不得写一套 handwritten Service 业务逻辑再“顺便”调用 RuleView。

## 3. required / min/max / 长度 / regex / 枚举 / expression

- `required`、`notNull/notEmpty` 在 transport 层验证；
- 数值 `min/max` 与 YAML 完全一致，并确保最小值不大于最大值；
- `minLength/maxLength` 与 YAML 完全一致；
- `regex` 必须使用 YAML 中声明的正则表达式，不得在代码中维护另一份 pattern；
- `relEnum` 由公共 Enum 驱动，不能另复制 allowed values；
- inline enum 仅用于没有公共 Enum 的局部集合；
- `request.validations[type=expression]` 只用于不依赖业务状态的传输层组合约束，必须在进入业务 RuleView 前执行；
- `@NotNull`、`@Min`、`@Max`、`@Size`、`@Pattern` 等生成约束必须带明确 `message`，优先使用 YAML 的 `validation.message`，缺省时使用确定性的字段说明和校验类型生成默认消息；
- “订单状态必须为待支付”“订单类型必须允许提交”“订单可支付”等业务事实不属于 API expression。它们必须由 RuleView/Information/Dependency 承载，并在 DTO 校验之后执行；
- Controller/DTO 不能复制业务组合判断。endpoint 只负责 transport mapping、字段校验和调用已绑定的业务 Service/RuleView。

## 4. Response

DTO/Serializer 可以是技术实现，但 `modelRef`、field required、relEnum 与外部值不得漂移。额外公开字段属于契约变更，应先修订 canonical YAML。

## 5. Binding

API Design ID 绑定真实 endpoint/handler symbol；RuleView Design ID 继续绑定 Runtime capability 或 RuleView 承载点，不用 Controller 代替全部业务节点。

## 6. Controller / Service 分组（工程默认，可覆盖）

保持所属 System 与 API 契约是硬要求；Controller/Service 的 Java 类边界是技术约定。默认按 System + URL 主资源分组，优先沿用项目已明确的子资源或业务能力边界，不机械地按每个方法一个文件。当前 Demo 已选定的资源分组保持不变。

本仓库生成器默认跳过前导 `api`、`v数字` 段，以首个静态资源段生成类名前缀。例如 `/api/orders/{id}` 与 `/api/orders/{id}/payment` 默认进入 `OrdersController/OrdersService`。独立 UTF-8 properties 可以覆盖指定 API：

```properties
version=dec-generation/v1
api.group.API-ORDER-START-PAYMENT=OrderPayments
api.group.API-ORDER-RECEIVE-PAYMENT-RESULT=OrderPayments
api.group.API-ORDER-BACK-TO-PAYING=OrderPayments
```

使用 `generate ... --generation-config <path.properties>`；Java 调用使用 `generate(project, output, basePackage, new GenerationOptions(Map.of(apiId, classPrefix)))`。未配置 API 仍按默认规则归组；类名前缀必须匹配 `[A-Z][A-Za-z0-9]*`，生成器追加 Controller/Service 后缀。同 System 内不同 URL 资源可显式归入同一能力，不同 System 始终隔离。显式分组也可处理 `/` 等无默认资源段的 URL。

配置不属于 DEC canonical YAML，不能写入业务节点。它只控制 Controller/Service 分组，不改变 methodName、URL、HTTP method、参数、DTO、响应、校验或 System。未知 API ID、重复/未知键、非法类名、不支持版本、分组或方法冲突在清理旧生成文件前失败。显式组与隐式默认组若落入同一 Java 类名会报错；如确需合并，把该组所有成员显式配置为同一组，避免隐式合并。

非空覆盖会输出规范化的 `dec-generation-options.properties`，纳入生成 manifest；默认模式不新增该文件，保持既有产物兼容。源配置建议置于生成目录外并版本管理；外部配置也加入 Binding 证据采集的 `--input`。生成目录中的配置副本是重现记录，不是新的业务事实来源。

每个方法保留 Design ID、YAML/XML 定位。DTO 按 API 契约生成，不随分组合并字段。索引和 Binding 必须对应新的文件与方法；手写 ServiceImpl 迁移到新接口，避免旧 Bean 留存。先验证配置，再清理 manifest 拥有的旧文件；手写文件不自动迁移或删除。
