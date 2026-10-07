package com.dec.lite.query;

import java.util.List;

public record SqlStatement(String sql, List<TypedParameter> parameters) {
    public SqlStatement { parameters = List.copyOf(parameters); }
}
