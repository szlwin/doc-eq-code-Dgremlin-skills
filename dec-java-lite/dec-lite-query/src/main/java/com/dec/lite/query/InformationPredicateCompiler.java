package com.dec.lite.query;

import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationDefinition;
import com.dec.lite.information.InformationExpression;
import com.dec.lite.information.InformationKey;
import com.dec.lite.information.InformationKind;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative compiler: only proven necessary conditions are pushed to SQL. */
public final class InformationPredicateCompiler {
    public enum Coverage { FULL, PARTIAL, RUNTIME_ONLY, UNQUERYABLE }
    public record Result(SqlPredicate predicate, Coverage coverage, List<InformationKey> postFilter) {
        public Result { postFilter = List.copyOf(postFilter); }
    }
    private static final Pattern SIMPLE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)\\s*(=|!=)\\s*(null|true|false|-?[0-9]+(?:\\.[0-9]+)?|'[^']*'|\"[^\"]*\")$", Pattern.CASE_INSENSITIVE);
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
        String source = definition.ruleData().replaceAll("\\s+", " ").trim();
        if (Pattern.compile("(?i)\\b(or|not)\\b").matcher(source).find()) return runtime(key);
        String[] clauses = source.split("(?i)\\s+and\\s+");
        List<SqlPredicate> pushed = new ArrayList<>();
        int matched = 0;
        for (String clause : clauses) {
            Matcher matcher = SIMPLE.matcher(clause.trim());
            if (!matcher.matches()) continue;
            // DEC's != treats a present null as unequal; SQL <> does not, so keep it in runtime.
            if ("!=".equals(matcher.group(2))) continue;
            try {
                SqlPredicate.Field field = view.field(matcher.group(1));
                Object value = literal(matcher.group(3));
                pushed.add(new SqlPredicate.Compare(field, matcher.group(2), value == null ? List.of() :
                        List.of(new TypedParameter("info" + parameterNumber.incrementAndGet(), field.type(), value, false))));
                matched++;
            } catch (QueryCompilationException ignored) { /* An unresolvable path stays in the runtime predicate. */ }
        }
        if (matched == 0) return runtime(key);
        boolean full = matched == clauses.length;
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
    private static Object literal(String text) {
        if ("null".equalsIgnoreCase(text)) return null;
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) return Boolean.valueOf(text);
        if (text.startsWith("'") || text.startsWith("\"")) return text.substring(1, text.length() - 1);
        return text.contains(".") ? new java.math.BigDecimal(text) : Long.valueOf(text);
    }
    private static Result runtime(InformationKey key) { return new Result(SqlPredicate.all(), Coverage.RUNTIME_ONLY, List.of(key)); }
    private static Result runtimeExpression() { return new Result(SqlPredicate.all(), Coverage.RUNTIME_ONLY, List.of()); }
}
