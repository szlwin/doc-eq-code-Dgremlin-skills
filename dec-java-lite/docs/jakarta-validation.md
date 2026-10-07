# DEC validation mapping

API body parameters are emitted as DTO fields:

- `required` / `notNull` -> `@NotNull`
- `notEmpty` -> `@NotEmpty`
- `min` / `max` -> `@Min` / `@Max`
- `minLength` / `maxLength` -> `@Size(min = ...)` / `@Size(max = ...)`
- `regex` / `pattern` -> `@Pattern`
- inline `enum` -> a generated `@AssertTrue` method

Request-level `expression` validations use the supported `and`, `or`, `=`, `!=`, parentheses, field and literal subset and are emitted as `@AssertTrue`. Public `relEnum` fields are compared through their serialized enum value.

Non-body parameters receive the supported annotation constraints directly on the Controller parameter. Response field constraints, complete DEC expressions, and unsupported validation types produce `DESIGN_GAP` instead of being silently dropped.

Controllers carrying a body DTO use `@Valid`.
