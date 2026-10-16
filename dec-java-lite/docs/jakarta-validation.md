# DEC validation mapping

API body parameters are emitted as DTO fields. The generated annotations are transport validation only; they must not encode reusable business state transitions or domain decisions.

- `required` / `notNull` -> `@NotNull(message = "...")`
- `notEmpty` -> `@NotEmpty(message = "...")`
- `min` / `max` -> `@Min(..., message = "...")` / `@Max(..., message = "...")`
- `minLength` / `maxLength` -> `@Size(min = ..., message = "...")` / `@Size(max = ..., message = "...")`
- `regex` / `pattern` -> `@Pattern(..., message = "...")`
- inline `enum` -> a generated `@AssertTrue` method

Inline enum validations use the supported `and`, `or`, `=`, `!=`, parentheses, field and literal subset and may be emitted as `@AssertTrue`. Request-level expressions are reserved for transport-only combinations. Domain combinations such as an order status/type rule belong in a RuleView or Information and are executed after DTO validation. Public `relEnum` fields are compared through their serialized enum value.

Non-body parameters receive the supported annotation constraints directly on the Controller parameter. Response field constraints, complete DEC expressions, and unsupported validation types produce `DESIGN_GAP` instead of being silently dropped.

Controllers carrying a body DTO use `@Valid`.
