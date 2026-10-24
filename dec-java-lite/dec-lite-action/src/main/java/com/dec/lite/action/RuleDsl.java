package com.dec.lite.action;

import com.dec.lite.information.RuleViewRegistry;
import java.util.*;

/** Deterministic DSL assignments and collection updates. All persistent writes use Invocation.write. */
final class RuleDsl {
    @FunctionalInterface interface Statement {
        void execute(RuleViewRegistry.Invocation invocation, Map<String, Object> variables, String prefix);
    }
    record Program(List<Statement> statements) {
        Program { statements = List.copyOf(statements); }
        void execute(RuleViewRegistry.Invocation invocation, Map<String, Object> variables, String prefix) {
            statements.forEach(s -> s.execute(invocation, variables, prefix));
        }
    }
    static Program compile(String source) {
        if (source == null || source.isBlank()) throw new ActionExecutionException("dsl requires process");
        List<Statement> statements = new ArrayList<>();
        for (String statement : RuleText.split(source, ';')) {
            if (statement.isBlank()) continue;
            if (statement.startsWith("every(") && statement.endsWith(")")) {
                var parts = RuleText.split(statement.substring(6, statement.length() - 1), ',');
                if (parts.size() != 2) throw new ActionExecutionException("every requires list and assignment");
                String path = parts.get(0); validatePath(path, false); Program body = compile(parts.get(1));
                statements.add((invocation, vars, prefix) -> {
                    Object value = invocation.context().read(prefix + path);
                    if (!(value instanceof List<?> list)) throw new ActionExecutionException("every requires list: " + path);
                    int size = list.size();
                    for (int index = 0; index < size; index++) body.execute(invocation, vars, prefix + path + "[" + index + "].");
                });
            } else {
                var parts = RuleText.split(statement, ':');
                if (parts.size() != 2) throw new ActionExecutionException("DSL requires one assignment: " + statement);
                String path = parts.get(0); RuleExpression.Expression value = RuleExpression.compile(parts.get(1));
                if (path.startsWith("error(") && path.endsWith(")")) {
                    var args = RuleText.split(path.substring(6, path.length() - 1), ',');
                    if (args.size() != 2) throw new ActionExecutionException("error requires code and message");
                    RuleExpression.Expression code = RuleExpression.compile(args.get(0));
                    RuleExpression.Expression message = RuleExpression.compile(args.get(1));
                    statements.add((invocation, vars, prefix) -> {
                        RuleExpression.Scope current = scope(invocation, vars, prefix);
                        if (!RuleExpression.bool(value.evaluate(current)))
                            throw new ActionExecutionException("[" + code.evaluate(current) + "] " + message.evaluate(current));
                    });
                } else {
                    validatePath(path, true);
                    statements.add((invocation, vars, prefix) -> {
                        Object result = value.evaluate(scope(invocation, vars, prefix));
                        if (path.startsWith("#")) vars.put(path, result);
                        else invocation.write(prefix + path, result);
                    });
                }
            }
        }
        if (statements.isEmpty()) throw new ActionExecutionException("empty DSL program");
        return new Program(statements);
    }
    private static RuleExpression.Scope scope(RuleViewRegistry.Invocation invocation, Map<String, Object> variables, String prefix) {
        if (prefix.isEmpty()) return new RuleExpression.Scope(invocation.context().values(), variables);
        Object value = invocation.context().read(prefix.substring(0, prefix.length() - 1));
        if (!(value instanceof Map<?, ?> map)) throw new ActionExecutionException("DSL collection item must be object");
        return RuleExpression.Scope.isolatedRow(map, variables);
    }
    private static void validatePath(String path, boolean variable) {
        if (variable && path.matches("#[A-Za-z_][A-Za-z0-9_]*")) return;
        if (!path.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\[\\d+])?(?:\\.[A-Za-z_][A-Za-z0-9_]*(?:\\[\\d+])?)*"))
            throw new ActionExecutionException("invalid DSL assignment target " + path);
    }
}
