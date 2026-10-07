package com.dec.lite.query;

public final class QueryCompilationException extends com.dec.lite.runtime.RuntimeFailure {
    public QueryCompilationException(String message) { super(com.dec.lite.runtime.RuntimeErrorCode.COMPILATION, null, message); }
}
