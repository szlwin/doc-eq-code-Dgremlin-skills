# DEC Canonical YAML：Business / P3 Directory

仅在编写或修改 `kind: business` 时读取。Business 通过 system-qualified InformationKey 引用 System-owned Information。

# 9. BusinessScope / Directory

Directory 只允许位于 `business.directories`。旧顶层 `directories:` 与 XML `directory-config` 已删除。

```yaml
kind: business

business:
  id: BUS-ORDER-PAYMENT
  name: order-payment
  desc: 订单支付业务流程
  directories:
    - ...
```

## 9.1 Business 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 ID |
| `name` | 是 | BusinessScope 名称 |
| `desc` | 推荐 | BusinessScope 业务说明；生成 `<business-config desc="...">` |
| `directories` | 是 | 目录列表，至少一个 |

BusinessScope 负责跨 System 编排，不拥有 Information。

## 9.2 Directory 字段

```yaml
- id: DIR-PAYING
  name: paying
  informationRef: order.paying
  modelRef: OrderInfo
  dependencies: ...
  subDirectories: ...
  actions: ...
  change: ...
```

| 字段 | 必填 | 说明 |
|---|---:|---|
| `name` | 是 | Directory 唯一名称 |
| `informationRef` | 是 | 目录完成后必须成立的 Information |
| `modelRef` | 是 | Directory 操作的业务模型上下文 |
| `isRoot` | 否 | 根目录标识 |
| `type` | 否 | 例如 `result` |
| `subDirectories` | 否 | P3 子目录/前置路径/分类关系 |
| `dependencies` | 否 | 进入前必须成立的 Information |
| `actions` | 否 | 有序 Action |
| `change` | 否 | 进入后显式物化 Information |

## 9.3 当前 P3 subDirectory 方向

`docs/directory.md` 的早期示例使用“父目录声明后继子目录”的展示方式；当前 `dev_p3` mix 约定采用**子目录执行完成后向父目录执行**的 P3 结构。因此 canonical YAML 按当前 P3 结构书写：

```yaml
# ordered -> paying
- name: paying
  subDirectories:
    - rel: ordered
```

不要根据旧示意图反转当前 P3 XML。

## 9.4 Result / Case

当前 P3 推荐：

```yaml
- name: PayResult
  type: result
  subDirectories:
    - rel: paying
    - rel: success
      role: case
      informationRef: payment.success
    - rel: error
      role: case
      informationRef: payment.error
```

- 普通 `subDirectory` 表示默认执行路径；
- `role: case` 表示结果分类；
- case 应有可识别 `informationRef`；
- **case 目标 Directory 不得同时声明 `dependencies`**：case `informationRef` 已经是该分支的 gate，再配置 depend 会形成重复/冲突判定；
- 一个 result 的互斥/完整性应在编译验证中检查。

兼容字段 `mutualExclusion` / `anyOne` 仅在现有 Compiler 明确支持时使用，新 P3 设计优先 `role: case` + Information 语义。

---

# 10. Dependency

```yaml
dependencies:
  - informationRef: order.payable
  - informationRef: user.effective
```

语义方向是 `Directory -> prerequisite Information`：Directory 依赖这些 Information 成立后才能进入。该方向也用于生成的 Business Directory Map。

语义：多个 Dependency 默认 AND。

硬规则：

- 只能引用 Information；
- 禁止把底层字段表达式复制进 Dependency；
- 引用必须存在；
- Dependency 在 Action 之前校验。

---

# 11. Action

## 11.1 RuleView Action

```yaml
actions:
  - id: ACT-START-PAY
    name: startPay
    systemRef: payment
    ruleRef: pay
```

当前跨 System BusinessScope 中：

- `name`：目录层业务操作名称；
- `systemRef`：RuleView owner System；
- `ruleRef`：该 System 下 RuleView name。

`name` 和 `ruleRef` 可以相同，也可以不同。

## 11.2 Custom Action

```yaml
- id: ACT-SMS-NOTIFY
  name: smsNotify
```

无 `ruleRef` 表示项目自定义 Action，需要 `dec-implementation` 提供实现并建立 Binding。

规则：

- 不允许 RuleView 找不到后自动回退到同名 Custom Action；
- 不允许 Custom Action 未注册时自动尝试同名 RuleView；
- Custom Action name 在注册域应唯一。

## 11.3 Action 执行顺序

`actions` 是有序列表，默认按 YAML 顺序执行。前一 Action 失败时，不继续后续 Action，除非未来 grammar 显式引入失败继续策略。

---

# 12. Produce

```yaml
produces:
  - id: PROD-PAYMENT-INFO
    ref: PaymentInfo
    informationRef: payment.paymentInfo
```

字段：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `ref` | 是 | Action 必须产生的数据/模型节点/结果对象 |
| `informationRef` | 条件必填 | 产出数据在信息树中对应的原子 Information |

什么时候 `informationRef` 必填：

- 后续被 Dependency 使用；
- 后续被 Directory 使用；
- 后续参与 Information expression；
- 用于结果分类/路径选择；
- 用于判断流程能否继续。

什么时候可以省略：

- 纯日志/错误记录；
- 仅返回但不参与后续业务判断；
- 内部临时数据。

Produce 不应直接映射到 Action 单独无法保证成立的复合 Information。

---

# 13. Change

P3 canonical：

```yaml
change:
  informationRef: order.paying
```

含义：Action/Produce 完成后物化该 Information。

当前 canonical 不推荐在 Business Directory 中直接写原始 process；状态物化优先通过 System-owned Information 的 `changeData` 表达，再由 `change.informationRef` 引用。

---

# 14. Back

Back 定义在 `subDirectories[].back`：

```yaml
subDirectories:
  - rel: paying
    back:
      name: returnPaying
      actions:
        - id: ACT-RESET-PAY-RESULT
          name: resetPayResult
          systemRef: payment
          ruleRef: resetPayResult
```

规则：

- Back 只能存在于真实父子关系；
- Back Action 与普通 Action 规则相同；
- 多级回退必须逐级执行，不允许跳过中间补偿；
- 返回后父目录 Information 必须恢复/重新成立。

---
