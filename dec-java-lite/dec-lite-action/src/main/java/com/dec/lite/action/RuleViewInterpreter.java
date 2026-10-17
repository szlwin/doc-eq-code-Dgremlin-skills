package com.dec.lite.action;

import com.dec.lite.information.*;
import java.util.*;

/** Ordered RuleView execution; data reads and writes are explicitly delegated to a DataSource. */
public final class RuleViewInterpreter {
    private final DataOperationAdapter adapter;
    public RuleViewInterpreter(DataOperationAdapter adapter) { this.adapter = adapter; }

    public RecognitionResult execute(CompiledRuleView view, RuleViewRegistry.Invocation invocation) {
        Map<String,Object> variables = new LinkedHashMap<>();
        for (CompiledRule definition : view.rules()) {
            CompiledRule rule = definition.withDataSource(invocation.resolveDataSource(definition.dataSource()));
            RecognitionResult result;
            try {
                result = evaluate(rule, invocation, variables);
                if (result == null) result = RecognitionResult.error(invocation.context().version(), "Rule returned null: " + rule.name());
            } catch (SecurityException denied) {
                result = RecognitionResult.error(invocation.context().version(), "ACCESS_DENIED " + rule.id() + ": " + denied.getMessage());
            } catch (RuleExpression.MissingValue missing) {
                result = RecognitionResult.unresolved(invocation.context().version(), missing.getMessage());
            } catch (RuntimeException failure) {
                result = RecognitionResult.error(invocation.context().version(), "Rule " + rule.id() + " failed: " + failure.getMessage());
            }
            if (result.status() != RecognitionResult.Status.TRUE) return withError(rule, result);
        }
        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), view.key().toString());
    }
    private RecognitionResult evaluate(CompiledRule rule, RuleViewRegistry.Invocation invocation, Map<String,Object> variables) {
        return switch (rule.type()) {
            case "check" -> result(RuleChecks.test(invocation.context().read(rule.property()), rule.pattern()), rule, invocation);
            case "checkPattern" -> result(RuleExpression.bool(RuleExpression.compile(rule.pattern()).evaluate(
                    new RuleExpression.Scope(invocation.context().values(), variables))), rule, invocation);
            case "checkData", "checkDataPattern" -> checkData(rule, invocation, variables);
            case "dsl" -> {
                RuleDsl.compile(rule.process()).execute(invocation, variables, "");
                yield result(true, rule, invocation);
            }
            case "insert", "update", "delete", "get", "query" -> {
                if (invocation.information() != null && !Set.of("get", "query").contains(rule.type()))
                    throw new ActionExecutionException("Information recognition cannot execute mutating Rule: " + rule.name());
                requireAdapter(rule);
                yield adapter.execute(rule, invocation);
            }
            default -> throw new ActionExecutionException("unsupported Rule type: " + rule.type());
        };
    }
    private RecognitionResult checkData(CompiledRule rule, RuleViewRegistry.Invocation invocation, Map<String,Object> variables) {
        requireAdapter(rule);
        // Read validation is transient: adapter and aliases cannot modify the working model or produced values.
        ModelContext snapshot = invocation.context().copyWithGuard(path -> { throw new SecurityException("Data validation cannot write " + path); });
        Map<String,Object> before = snapshot.values();
        var read = new RuleViewRegistry.Invocation(invocation.key(), invocation.information(), snapshot, invocation.payload(),
                new MutationSet(), new LinkedHashMap<>(), invocation.transaction(), invocation.dataSourceOverride(), invocation.defaultDataSource());
        List<Map<String,Object>> rows = adapter.read(rule, read);
        if (rows == null) throw new ActionExecutionException("DataSource read returned null rows: " + rule.id());
        if (!snapshot.values().equals(before) || !read.produced().isEmpty() || !read.mutations().mutations().isEmpty())
            throw new ActionExecutionException("DataSource read must not modify the model or produce data: " + rule.id());
        if (rows.isEmpty()) return result(rule.type().equals("checkData") && RuleChecks.test(null, rule.pattern()), rule, invocation);
        var expression = rule.type().equals("checkDataPattern") ? RuleExpression.compile(rule.pattern()) : null;
        for (Map<String,Object> row : rows) {
            if (row == null) throw new ActionExecutionException("DataSource read returned null row: " + rule.id());
            boolean passed;
            if (expression == null) passed = RuleChecks.test(row, rule.pattern());
            else {
                Map<String,Object> scope = new LinkedHashMap<>(invocation.context().values());
                scope.putAll(row); // Legacy query aliases temporarily shadow same-named model fields.
                passed = RuleExpression.bool(expression.evaluate(new RuleExpression.Scope(scope, variables)));
            }
            if (!passed) return result(false, rule, invocation);
        }
        return result(true, rule, invocation);
    }
    private void requireAdapter(CompiledRule rule) {
        if (adapter == null) throw new ActionExecutionException("DataOperationAdapter is not registered for Rule " + rule.name());
    }
    private static RecognitionResult result(boolean passed, CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        return RecognitionResult.of(passed ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, invocation.context().version(), rule.id());
    }
    private static RecognitionResult withError(CompiledRule rule, RecognitionResult result) {
        if (rule.error() == null) return result;
        var errors = new ArrayList<>(result.errors());
        var error = rule.error();
        errors.add("Rule " + rule.id() + " [" + error.code() + "] " + error.message()
                + (error.level() == null ? "" : " (level=" + error.level() + ")"));
        return new RecognitionResult(result.status(), result.evidence(), result.dependencies(), errors, result.modelVersion(), result.trace());
    }
}
