package com.dec.lite.runtime;

/** Stable failure categories shared by compilation and execution layers. */
public enum RuntimeErrorCode {
    COMPILATION, VALIDATION, DEPENDENCY, ACTION, PRODUCE, CLASSIFICATION,
    QUERY, TRANSACTION, ACCESS_DENIED
}
