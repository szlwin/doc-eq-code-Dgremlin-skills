package com.dec.lite.information;

/** Caller-owned transaction scope shared with RuleView data adapters. */
public interface TransactionScope {
    default Object resource(String name) { return null; }
    default void commit() { }
    default void rollback() { }
}
