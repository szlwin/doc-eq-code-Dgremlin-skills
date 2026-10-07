# API 实现契约

API 负责传输层契约，不是业务 Rule 的副本。

## 1. 必须保持的事实

- URL / HTTP method；
- parameter `in/type/required/default`；
- `min/max/minLength/maxLength/pattern/regex`；
- inline `enum` 或公共 `relEnum`；
- request-level `expression`；
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
- `request.validations[type=expression]` 是参数组合约束，例如 `status = 1 and type = 1`，必须在进入业务 RuleView 前执行；
- “订单可支付”等业务事实不属于 API expression，应由 RuleView/Information/Dependency 负责。

## 4. Response

DTO/Serializer 可以是技术实现，但 `modelRef`、field required、relEnum 与外部值不得漂移。额外公开字段属于契约变更，应先修订 canonical YAML。

## 5. Binding

API Design ID 绑定真实 endpoint/handler symbol；RuleView Design ID 继续绑定 Runtime capability 或 RuleView 承载点，不用 Controller 代替全部业务节点。
