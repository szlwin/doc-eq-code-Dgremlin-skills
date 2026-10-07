package com.dec.lite.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC adapter uses PreparedStatement only; connection ownership is per invocation. */
public final class JdbcQueryExecutor implements QueryExecutor, DataCommandExecutor {
    private final ConnectionRouter router;
    public JdbcQueryExecutor(ConnectionRouter router) { this.router = router; }
    public List<Map<String, Object>> query(SqlStatement statement, ConnectionRoute route) {
        try (Connection connection = router.open(route); PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
            bind(prepared, statement.parameters());
            try (ResultSet result = prepared.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>(); ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) row.put(metadata.getColumnLabel(index), result.getObject(index));
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException failure) { throw new QueryExecutionException("SQL query failed", failure); }
    }
    public int execute(SqlStatement statement, ConnectionRoute route) {
        try (Connection connection = router.open(route); PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
            bind(prepared, statement.parameters()); return prepared.executeUpdate();
        } catch (SQLException failure) { throw new QueryExecutionException("SQL command failed", failure); }
    }
    private static void bind(PreparedStatement prepared, List<TypedParameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) prepared.setObject(index + 1, parameters.get(index).value());
    }
}
