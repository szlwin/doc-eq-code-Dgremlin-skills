package com.dec.lite.action;

/** Instance-scoped transaction boundary owned by the caller/runtime. */
public interface ActionTransaction extends com.dec.lite.information.TransactionScope {
    default void commit() { }
    default void rollback() { }
}
