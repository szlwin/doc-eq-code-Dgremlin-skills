package com.dec.lite.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** JDBC adapter uses PreparedStatement only; connection ownership is per invocation. */
public final class JdbcQueryExecutor implements QueryExecutor, DataCommandExecutor {
    private final ConnectionRouter router;
    public JdbcQueryExecutor(ConnectionRouter router) { this.router = router; }
    public List<Map<String, Object>> query(SqlStatement statement, ConnectionRoute route) {
        try (Connection connection = router.open(route); PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
            JdbcRows.bind(prepared, statement.parameters());
            try (ResultSet result = prepared.executeQuery()) {
                return JdbcRows.read(result);
            }
        } catch (SQLException failure) { throw new QueryExecutionException("SQL query failed", failure); }
    }
    public int execute(SqlStatement statement, ConnectionRoute route) {
        try (Connection connection = router.open(route); PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
            JdbcRows.bind(prepared, statement.parameters()); return prepared.executeUpdate();
        } catch (SQLException failure) { throw new QueryExecutionException("SQL command failed", failure); }
    }
}
