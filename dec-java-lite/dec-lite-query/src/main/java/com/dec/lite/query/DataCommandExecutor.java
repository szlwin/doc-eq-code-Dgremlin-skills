package com.dec.lite.query;

public interface DataCommandExecutor {
    int execute(SqlStatement statement, ConnectionRoute route);
}
