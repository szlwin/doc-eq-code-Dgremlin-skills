package com.dec.lite.query;

/** Compiled link from a DataSource to a named connection. */
public record ConnectionRoute(String connection, String dataSource, String type) {
    public ConnectionRoute {
        if (connection == null || connection.isBlank() || dataSource == null || dataSource.isBlank())
            throw new IllegalArgumentException("connection and DataSource are required");
    }
}
