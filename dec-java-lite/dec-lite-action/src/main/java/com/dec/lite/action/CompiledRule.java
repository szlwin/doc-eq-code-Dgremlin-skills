package com.dec.lite.action;

import com.dec.lite.model.CanonicalValues;
import java.util.Map;
import java.util.Objects;

/** Immutable Rule metadata and reusable programs; request state stays in Invocation. */
public final class CompiledRule {
    private final String id, name, type, property, pattern, process, dataSource, cmd;
    private final RuleError error;
    private final Map<String, Object> customer;
    private final RuleExpression.Expression expression;
    private final RuleDsl.Program program;
    private final RuleChecks.Program checks;

    public CompiledRule(String id, String name, String type, String property, String pattern,
                        String process, String dataSource, String cmd, RuleError error, Map<String, Object> customer) {
        this(id, name, type, property, pattern, process, dataSource, cmd, error, customer,
                "checkPattern".equals(type) || "checkDataPattern".equals(type) ? RuleExpression.compile(pattern) : null,
                "dsl".equals(type) ? RuleDsl.compile(process) : null,
                "check".equals(type) || "checkData".equals(type) ? RuleChecks.compile(pattern) : null);
    }
    private CompiledRule(String id, String name, String type, String property, String pattern,
                         String process, String dataSource, String cmd, RuleError error, Map<String, Object> customer,
                         RuleExpression.Expression expression, RuleDsl.Program program, RuleChecks.Program checks) {
        this.id = id; this.name = name; this.type = type; this.property = property; this.pattern = pattern;
        this.process = process; this.dataSource = dataSource; this.cmd = cmd; this.error = error;
        this.customer = CanonicalValues.map(customer);
        if (this.customer.values().stream().anyMatch(value -> !(value instanceof String)))
            throw new ActionExecutionException("Rule customer must contain flat string values (XML attributes)");
        this.expression = expression; this.program = program; this.checks = checks;
    }
    public CompiledRule(String id, String name, String type, String property, String pattern,
                        String process, String dataSource) {
        this(id, name, type, property, pattern, process, dataSource, null, null, Map.of());
    }
    public String id() { return id; }
    public String name() { return name; }
    public String type() { return type; }
    public String property() { return property; }
    public String pattern() { return pattern; }
    public String process() { return process; }
    public String dataSource() { return dataSource; }
    public String cmd() { return cmd; }
    public RuleError error() { return error; }
    public Map<String, Object> customer() { return customer; }
    RuleExpression.Expression expression() { return expression; }
    RuleDsl.Program program() { return program; }
    RuleChecks.Program checks() { return checks; }

    public CompiledRule withDataSource(String source) {
        if (Objects.equals(source, dataSource)) return this;
        return new CompiledRule(id, name, type, property, pattern, process, source, cmd, error, customer, expression, program, checks);
    }
    public boolean external() {
        return java.util.Set.of("checkData", "checkDataPattern", "get", "query", "insert", "update", "delete").contains(type);
    }
    // Preserve metadata value semantics; program lambdas are implementation details.
    @Override public boolean equals(Object other) {
        if (!(other instanceof CompiledRule r)) return false;
        return Objects.equals(id, r.id) && Objects.equals(name, r.name) && Objects.equals(type, r.type)
                && Objects.equals(property, r.property) && Objects.equals(pattern, r.pattern)
                && Objects.equals(process, r.process) && Objects.equals(dataSource, r.dataSource)
                && Objects.equals(cmd, r.cmd) && Objects.equals(error, r.error) && customer.equals(r.customer);
    }
    @Override public int hashCode() { return Objects.hash(id, name, type, property, pattern, process, dataSource, cmd, error, customer); }
    @Override public String toString() { return "CompiledRule[id=" + id + ", name=" + name + ", type=" + type + ", dataSource=" + dataSource + "]"; }

    public record RuleError(String code, String message, String level) {
        public RuleError {
            if (code == null || code.isBlank() || message == null || message.isBlank())
                throw new ActionExecutionException("Rule error requires code and message");
        }
    }
}
