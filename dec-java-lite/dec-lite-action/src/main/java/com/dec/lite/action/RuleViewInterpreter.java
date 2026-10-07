package com.dec.lite.action;

import com.dec.lite.information.ModelExpressionCompiler;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;

import java.util.List;

/** Ordered RuleView interpreter. External operations require an explicit adapter. */
public final class RuleViewInterpreter {
    private final DataOperationAdapter adapter;
    private final ModelExpressionCompiler expressions = new ModelExpressionCompiler();
    public RuleViewInterpreter(DataOperationAdapter adapter) { this.adapter = adapter; }

    public RecognitionResult execute(CompiledRuleView view, RuleViewRegistry.Invocation invocation) {
        for (CompiledRule rule : view.rules()) {
            RecognitionResult result = evaluate(rule, invocation);
            if (result == null) return RecognitionResult.error(invocation.context().version(), "Rule returned null: " + rule.name());
            if (result.status() != RecognitionResult.Status.TRUE) return result;
        }
        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), view.key().toString());
    }

    private RecognitionResult evaluate(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        String version = invocation.context().version();
        return switch (rule.type()) {
            case "check", "checkData" -> check(rule, invocation);
            case "checkPattern", "checkDataPattern" -> pattern(rule, invocation);
            case "dsl" -> dsl(rule, invocation);
            case "insert", "update", "delete", "get", "query" -> {
                if (invocation.information() != null && !List.of("get", "query").contains(rule.type())) yield RecognitionResult.error(version, "Information recognition cannot execute mutating Rule: " + rule.name());
                if (adapter == null) yield RecognitionResult.error(version, "DataOperationAdapter is not registered for Rule " + rule.name());
                yield adapter.execute(rule, invocation);
            }
            default -> RecognitionResult.error(version, "unsupported Rule type: " + rule.type());
        };
    }
    private RecognitionResult check(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        if (rule.property() == null || rule.pattern() == null) return RecognitionResult.error(invocation.context().version(), "check requires property and pattern: " + rule.name());
        Object value = invocation.context().read(rule.property());
        boolean success = switch (rule.pattern().toUpperCase()) {
            case "NOTNULL" -> value != null;
            case "NULL" -> value == null;
            default -> false;
        };
        if (!rule.pattern().equalsIgnoreCase("NOTNULL") && !rule.pattern().equalsIgnoreCase("NULL")) return pattern(new CompiledRule(rule.id(), rule.name(), "checkPattern", null, rule.property() + " = " + rule.pattern(), null, rule.dataSource()), invocation);
        return RecognitionResult.of(success ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, invocation.context().version(), rule.property());
    }
    private RecognitionResult pattern(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        if (rule.pattern() == null) return RecognitionResult.error(invocation.context().version(), "checkPattern requires pattern: " + rule.name());
        String normalized = rule.pattern().replaceAll("(?<=\\s)=\\s*([A-Z][A-Z0-9_]*)\\b", "= '$1'");
        return expressions.compile(normalized).evaluate(invocation.context());
    }
    private RecognitionResult dsl(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        if (rule.process() == null) return RecognitionResult.error(invocation.context().version(), "dsl requires process: " + rule.name());
        for (String statement : rule.process().split(";")) {
            String value = statement.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("every(") && value.endsWith(")")) {
                String inner = value.substring(6, value.length() - 1);
                int comma = inner.indexOf(',');
                if (comma < 0) return RecognitionResult.error(invocation.context().version(), "invalid every DSL: " + value);
                String listPath = inner.substring(0, comma).trim();
                Object raw = invocation.context().read(listPath);
                if (!(raw instanceof List<?> list)) return RecognitionResult.error(invocation.context().version(), "every DSL path is not a list: " + listPath);
                Assignment assignment = assignment(inner.substring(comma + 1));
                for (int index = 0; index < list.size(); index++) invocation.write(listPath + "[" + index + "]." + assignment.path(), assignment.value());
            } else {
                Assignment assignment = assignment(value);
                invocation.write(assignment.path(), assignment.value());
            }
        }
        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.name());
    }
    private static Assignment assignment(String statement) {
        int colon = statement.indexOf(':');
        if (colon < 1) throw new ActionExecutionException("invalid DSL assignment: " + statement);
        String raw = statement.substring(colon + 1).trim();
        Object value;
        if (raw.equalsIgnoreCase("null")) value = null;
        else if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) value = Boolean.valueOf(raw);
        else if (raw.matches("-?\\d+")) value = Long.valueOf(raw);
        else if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\""))) value = raw.substring(1, raw.length() - 1);
        else throw new ActionExecutionException("unsupported DSL literal: " + raw);
        return new Assignment(statement.substring(0, colon).trim(), value);
    }
    private record Assignment(String path, Object value) { }
}
