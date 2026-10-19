package com.dec.lite.query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared JDBC value conversion; resource and transaction ownership stay with callers. */
final class JdbcRows {
    private JdbcRows() { }

    static void bind(PreparedStatement prepared, List<TypedParameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++)
            prepared.setObject(index + 1, parameters.get(index).value());
    }

    static List<Map<String, Object>> read(ResultSet result) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metadata = result.getMetaData();
        int columns = metadata.getColumnCount();
        while (result.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= columns; index++)
                row.put(metadata.getColumnLabel(index), result.getObject(index));
            rows.add(row);
        }
        return rows;
    }
}
