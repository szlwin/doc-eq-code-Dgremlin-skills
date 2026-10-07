package com.dec.lite.query;

import java.util.List;

/** Dialect boundary; QueryPlan and the generic translator are database neutral. */
public interface SqlDialect {
    String quote(String identifier);
    String pageClause(int offset, int size, List<TypedParameter> parameters);
}
