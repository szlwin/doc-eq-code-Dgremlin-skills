package com.dec.lite.query;

import java.util.List;

public final class AnsiSqlDialect implements SqlDialect {
    public String quote(String identifier) { return "\"" + valid(identifier) + "\""; }
    public String pageClause(int offset, int size, List<TypedParameter> parameters) {
        parameters.add(new TypedParameter("pageOffset", "integer", offset, false));
        parameters.add(new TypedParameter("pageSize", "integer", size, false));
        return " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    }
    static String valid(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new QueryCompilationException("unsafe SQL identifier: " + identifier);
        return identifier;
    }
}
