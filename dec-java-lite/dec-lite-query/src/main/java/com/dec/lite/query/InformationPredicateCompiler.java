package com.dec.lite.query;

import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationDefinition;
import com.dec.lite.information.InformationExpression;
import com.dec.lite.information.InformationKey;
import com.dec.lite.information.InformationKind;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Conservative compiler: only proven necessary conditions are pushed to SQL. */
public final class InformationPredicateCompiler {
    public enum Coverage { FULL, PARTIAL, RUNTIME_ONLY, UNQUERYABLE }
    public record Result(SqlPredicate predicate, Coverage coverage, List<InformationKey> postFilter) {
        public Result { postFilter = List.copyOf(postFilter); }
    }
    private final InformationCompilation information;
    private final ViewJoinPlanner view;
    private final String rootView;
    private final AtomicInteger parameterNumber;
    public InformationPredicateCompiler(InformationCompilation information, ViewJoinPlanner view, String rootView, AtomicInteger parameterNumber) {
        this.information = information; this.view = view; this.rootView = rootView; this.parameterNumber = parameterNumber;
    }
    public Result compile(InformationKey key) {
        InformationDefinition definition = information.definitions().get(key);
        if (definition == null) return new Result(SqlPredicate.all(), Coverage.UNQUERYABLE, List.of(key));
        if (definition.kind() == InformationKind.RULE_VIEW_ATOMIC) return runtime(key);
        if (definition.kind() == InformationKind.MODEL_EXPRESSION_ATOMIC) return model(key, definition);
        return expression(key, information.informationExpressions().get(key));
    }
    private Result model(InformationKey key, InformationDefinition definition) {
        if (!rootView.equals(definition.viewRef())) return runtime(key);
        var expression = information.modelExpressions().get(key);
        if (expression == null) return runtime(key);
        var projection = expression.necessaryEqualities();
        List<SqlPredicate> pushed = new ArrayList<>();
        int matched = 0;
        for (var condition : projection.conditions()) {
            try {
                SqlPredicate.Field field = view.field(condition.path());
                Object value = condition.value();
                pushed.add(new SqlPredicate.Compare(field, "=", value == null ? List.of() :
                        List.of(new TypedParameter("info" + parameterNumber.incrementAndGet(), field.type(), value, false))));
                matched++;
            } catch (QueryCompilationException ignored) { /* An unresolvable path stays in the runtime predicate. */ }
        }
        if (matched == 0) return runtime(key);
        boolean full = projection.complete() && matched == projection.conditions().size();
        return new Result(SqlPredicate.and(pushed), full ? Coverage.FULL : Coverage.PARTIAL,
                full ? List.of() : List.of(key));
    }
    private Result expression(InformationKey key, InformationExpression expression) {
        if (expression == null) return new Result(SqlPredicate.all(), Coverage.UNQUERYABLE, List.of(key));
        Result compiled = compileExpression(expression);
        if (compiled.coverage() == Coverage.FULL) return compiled;
        return new Result(compiled.predicate(), compiled.coverage(), List.of(key));
    }
    private Result compileExpression(InformationExpression expression) {
        return switch (expression.kind()) {
            case REFERENCE -> compile(expression.reference());
            case NOT -> runtimeExpression();
            case AND -> {
                Result left = compileExpression(expression.left()); Result right = compileExpression(expression.right());
                Coverage coverage = left.coverage() == Coverage.FULL && right.coverage() == Coverage.FULL ? Coverage.FULL :
                        left.coverage() == Coverage.UNQUERYABLE || right.coverage() == Coverage.UNQUERYABLE ? Coverage.UNQUERYABLE :
                        left.predicate() instanceof SqlPredicate.All && right.predicate() instanceof SqlPredicate.All ? Coverage.RUNTIME_ONLY : Coverage.PARTIAL;
                yield new Result(SqlPredicate.and(List.of(left.predicate(), right.predicate())), coverage, List.of());
            }
            case OR -> {
                Result left = compileExpression(expression.left()); Result right = compileExpression(expression.right());
                if (left.coverage() == Coverage.FULL && right.coverage() == Coverage.FULL)
                    yield new Result(SqlPredicate.or(List.of(left.predicate(), right.predicate())), Coverage.FULL, List.of());
                yield runtimeExpression();
            }
        };
    }
    private static Result runtime(InformationKey key) { return new Result(SqlPredicate.all(), Coverage.RUNTIME_ONLY, List.of(key)); }
    private static Result runtimeExpression() { return new Result(SqlPredicate.all(), Coverage.RUNTIME_ONLY, List.of()); }
}
