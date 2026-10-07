# DEC YAML → XML：API

`kind: api` → `<api-config>`。

API 基本字段：`name/desc/system/url/method` → `<api>` 同名属性。

## Parameter + relEnum

```yaml
- name: status
  in: body
  type: int
  required: true
  relEnum: OrderStatus
  validations:
    - type: notNull
```

→

```xml
<parameter name="status" in="body" type="int" required="true" rel-enum="OrderStatus">
  <validation-info>
    <validation type="notNull"/>
  </validation-info>
</parameter>
```

## Validation

映射：

- `type` → `validation/@type`
- `value` → `@value`
- `values` → 多个 `<value>`
- `expression` → `@expression`
- `message` → `@message`


常用 Validation 示例：

```yaml
validations:
  - type: min
    value: 1
  - type: max
    value: 100
  - type: regex
    value: "^[A-Z0-9_-]{6,32}$"
```

→

```xml
<validation-info>
  <validation type="min" value="1"/>
  <validation type="max" value="100"/>
  <validation type="regex" value="^[A-Z0-9_-]{6,32}$"/>
</validation-info>
```

跨字段表达式：

```yaml
request:
  validations:
    - type: expression
      expression: status = 1 and type = 1
```

→

```xml
<request>
  <validation-info>
    <validation type="expression" expression="status = 1 and type = 1"/>
  </validation-info>
  ...
</request>
```

## Response Field

`name/type/required/desc/relEnum/validations` 映射为 `<field>` 属性和可选 `validation-info`。`relEnum` → `rel-enum`。

RuleView `apiRef` 仍映射 `<rule-view-info api-ref="system.apiName">`，绑定可选。
