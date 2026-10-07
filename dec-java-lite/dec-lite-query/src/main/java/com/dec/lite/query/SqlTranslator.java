package com.dec.lite.query;

import java.util.ArrayList;
import java.util.List;

/** Generic SQL translator with dialect quoting and paging. */
public final class SqlTranslator {
    private final SqlDialect dialect;
    public SqlTranslator(SqlDialect dialect) { this.dialect = dialect; }
    public SqlStatement candidates(QueryPlan plan, int offset, int size) {
        List<TypedParameter> parameters = new ArrayList<>();
        String rootId = column("t0", plan.rootIdColumn());
        String sql = "SELECT DISTINCT " + rootId + " AS " + dialect.quote("__dec_id") +
                fromAndJoins(plan) + " WHERE " + predicate(plan.predicate(), parameters) +
                " ORDER BY " + rootId + dialect.pageClause(offset, size, parameters);
        return new SqlStatement(sql, parameters);
    }
    public SqlStatement details(QueryPlan plan, List<?> ids) {
        if (ids == null || ids.isEmpty()) throw new QueryCompilationException("detail query requires root IDs");
        if (ids.size() > 500) throw new QueryCompilationException("detail ID batch exceeds 500");
        List<String> columns = new ArrayList<>();
        columns.add(column("t0", plan.rootIdColumn()) + " AS " + dialect.quote("__dec_id"));
        for (Projection projection : plan.projections())
            columns.add(column(projection.alias(), projection.column()) + " AS " + dialect.quote(projection.resultAlias()));
        List<TypedParameter> parameters = new ArrayList<>();
        List<String> marks = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            marks.add("?"); parameters.add(new TypedParameter("id" + index, "integer", ids.get(index), false));
        }
        String sql = "SELECT " + String.join(", ", columns) + fromAndJoins(plan) +
                " WHERE " + column("t0", plan.rootIdColumn()) + " IN (" + String.join(", ", marks) + ")" +
                " ORDER BY " + column("t0", plan.rootIdColumn());
        return new SqlStatement(sql, parameters);
    }
    public String explain(QueryPlan plan) {
        SqlStatement sample = candidates(plan, 0, Math.min(plan.size(), 100));
        return String.join("\n", plan.trace()) + "\nSQL=" + sample.sql() + "\nparameters=" + sample.parameters();
    }
    private String fromAndJoins(QueryPlan plan) {
        StringBuilder result = new StringBuilder(" FROM ").append(dialect.quote(plan.rootTable())).append(" ").append(dialect.quote("t0"));
        for (JoinPlan join : plan.joins()) {
            result.append(" LEFT JOIN ").append(dialect.quote(join.table())).append(' ').append(dialect.quote(join.alias()));
            result.append(" ON ").append(column(join.parentAlias(), join.parentColumn()));
            result.append(" = ").append(column(join.alias(), join.childColumn()));
        }
        return result.toString();
    }
    private String predicate(SqlPredicate predicate, List<TypedParameter> parameters) {
        if (predicate instanceof SqlPredicate.All) return "1=1";
        if (predicate instanceof SqlPredicate.Group group) {
            List<String> parts = group.children().stream().map(child -> predicate(child, parameters)).toList();
            return "(" + String.join(" " + group.operator() + " ", parts) + ")";
        }
        SqlPredicate.Compare comparison = (SqlPredicate.Compare) predicate;
        String field = column(comparison.field().alias(), comparison.field().column());
        if (comparison.parameters().isEmpty()) return field + (comparison.operator().equals("!=") ? " IS NOT NULL" : " IS NULL");
        parameters.addAll(comparison.parameters());
        if (comparison.operator().equals("IN")) return field + " IN (" + String.join(", ", java.util.Collections.nCopies(comparison.parameters().size(), "?")) + ")";
        return field + " " + comparison.operator() + " ?";
    }
    private String column(String alias, String column) { return dialect.quote(alias) + "." + dialect.quote(column); }
}
