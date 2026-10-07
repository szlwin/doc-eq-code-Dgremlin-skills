package com.dec.lite.query;

import java.util.List;

/** SQL-neutral, identifier-safe predicate tree. Values are always parameters. */
public sealed interface SqlPredicate permits SqlPredicate.All, SqlPredicate.Compare, SqlPredicate.Group {
    record All() implements SqlPredicate { }
    record Compare(Field field, String operator, List<TypedParameter> parameters) implements SqlPredicate {
        public Compare { parameters = List.copyOf(parameters); if (!List.of("=", "!=", "IN").contains(operator)) throw new IllegalArgumentException("unsupported operator"); }
    }
    record Group(String operator, List<SqlPredicate> children) implements SqlPredicate {
        public Group { children = List.copyOf(children); if (!List.of("AND", "OR").contains(operator)) throw new IllegalArgumentException("unsupported group operator"); }
    }
    record Field(String alias, String column, String path, String type) { }
    static SqlPredicate all() { return new All(); }
    static SqlPredicate and(List<SqlPredicate> values) { return group("AND", values); }
    static SqlPredicate or(List<SqlPredicate> values) { return group("OR", values); }
    private static SqlPredicate group(String op, List<SqlPredicate> values) {
        List<SqlPredicate> reduced = values.stream().filter(value -> !(value instanceof All) || "OR".equals(op)).toList();
        if (reduced.isEmpty()) return all();
        if (reduced.size() == 1) return reduced.get(0);
        if ("OR".equals(op) && reduced.stream().anyMatch(value -> value instanceof All)) return all();
        return new Group(op, reduced);
    }
}
