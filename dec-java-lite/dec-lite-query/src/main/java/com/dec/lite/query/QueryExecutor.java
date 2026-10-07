package com.dec.lite.query;

import java.util.List;
import java.util.Map;

public interface QueryExecutor {
    List<Map<String, Object>> query(SqlStatement statement, ConnectionRoute route);
}
