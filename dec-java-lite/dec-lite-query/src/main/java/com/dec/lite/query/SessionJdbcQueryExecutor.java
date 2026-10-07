package com.dec.lite.query;

import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.TransactionResource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses one session-owned JDBC connection across candidate/detail queries and RuleView adapters. */
public final class SessionJdbcQueryExecutor implements QueryExecutor, DataCommandExecutor {
    private final ExecutionSession session;
    private final ConnectionRouter router;
    public SessionJdbcQueryExecutor(ExecutionSession session, ConnectionRouter router) {
        this.session = session; this.router = router;
    }
    public List<Map<String, Object>> query(SqlStatement statement, ConnectionRoute route) {
        try (PreparedStatement prepared = connection(route).prepareStatement(statement.sql())) {
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
        } catch (SQLException failure) { throw new QueryExecutionException("session SQL query failed", failure); }
    }
    public int execute(SqlStatement statement, ConnectionRoute route) {
        try (PreparedStatement prepared = connection(route).prepareStatement(statement.sql())) {
            bind(prepared, statement.parameters()); return prepared.executeUpdate();
        } catch (SQLException failure) { throw new QueryExecutionException("session SQL command failed", failure); }
    }
    private Connection connection(ConnectionRoute route) {
        return session.transactions().enlist(route.dataSource() + "/" + route.connection(), () -> {
            Connection connection = null;
            try {
                connection = router.open(route); connection.setAutoCommit(false);
                return new ConnectionResource(connection);
            } catch (SQLException failure) {
                if (connection != null) {
                    try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                }
                throw new RuntimeFailure(RuntimeErrorCode.TRANSACTION, route.dataSource() + "/" + route.connection(), null, "cannot open routed JDBC connection", failure);
            }
        }, Connection.class);
    }
    private static void bind(PreparedStatement prepared, List<TypedParameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) prepared.setObject(index + 1, parameters.get(index).value());
    }
    private record ConnectionResource(Connection connection) implements TransactionResource {
        public Object handle() { return connection; }
        public void commit() { try { connection.commit(); } catch (SQLException failure) { throw error("commit", failure); } }
        public void rollback() { try { connection.rollback(); } catch (SQLException failure) { throw error("rollback", failure); } }
        public void close() { try { connection.close(); } catch (SQLException failure) { throw error("close", failure); } }
        private RuntimeFailure error(String step, SQLException failure) { return new RuntimeFailure(RuntimeErrorCode.TRANSACTION, null, null, "JDBC " + step + " failed", failure); }
    }
}
