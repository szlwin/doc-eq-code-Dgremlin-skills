package com.dec.lite.action;

public class ActionExecutionException extends com.dec.lite.runtime.RuntimeFailure {
    public ActionExecutionException(String message) { super(com.dec.lite.runtime.RuntimeErrorCode.ACTION, null, null, message, null); }
    public ActionExecutionException(String message, Throwable cause) { super(com.dec.lite.runtime.RuntimeErrorCode.ACTION, null, null, message, cause); }
}
