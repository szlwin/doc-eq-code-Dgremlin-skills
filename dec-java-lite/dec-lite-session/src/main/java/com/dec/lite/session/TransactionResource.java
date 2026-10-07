package com.dec.lite.session;

/** One enlisted resource. A route may be enlisted only once per transaction. */
public interface TransactionResource extends AutoCloseable {
    Object handle();
    void commit();
    void rollback();
    @Override void close();
}
