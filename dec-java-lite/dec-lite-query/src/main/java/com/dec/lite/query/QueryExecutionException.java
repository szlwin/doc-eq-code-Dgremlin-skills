package com.dec.lite.query;

public final class QueryExecutionException extends com.dec.lite.runtime.RuntimeFailure {
    public QueryExecutionException(String message) { super(com.dec.lite.runtime.RuntimeErrorCode.QUERY, null, null, message, null); }
    public QueryExecutionException(String message, Throwable cause) { super(com.dec.lite.runtime.RuntimeErrorCode.QUERY, null, null, message, cause); }
}
