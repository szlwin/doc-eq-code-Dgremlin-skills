package com.dec.lite.information;

public class InformationCompilationException extends com.dec.lite.runtime.RuntimeFailure {
    public InformationCompilationException(String message) { super(com.dec.lite.runtime.RuntimeErrorCode.COMPILATION, null, null, message, null); }
    public InformationCompilationException(String message, Throwable cause) { super(com.dec.lite.runtime.RuntimeErrorCode.COMPILATION, null, null, message, cause); }
}
