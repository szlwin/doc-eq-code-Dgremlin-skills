package com.dec.lite.query;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionRouter {
    Connection open(ConnectionRoute route) throws SQLException;
}
