package com.dec.lite.query;

import java.util.List;

public final class MySqlDialect implements SqlDialect {
    public String quote(String identifier) { return "`" + AnsiSqlDialect.valid(identifier) + "`"; }
    public String pageClause(int offset, int size, List<TypedParameter> parameters) {
        parameters.add(new TypedParameter("pageSize", "integer", size, false));
        parameters.add(new TypedParameter("pageOffset", "integer", offset, false));
        return " LIMIT ? OFFSET ?";
    }
}
