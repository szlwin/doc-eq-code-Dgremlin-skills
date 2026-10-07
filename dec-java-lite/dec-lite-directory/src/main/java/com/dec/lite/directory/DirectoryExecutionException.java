package com.dec.lite.directory;

public final class DirectoryExecutionException extends com.dec.lite.runtime.RuntimeFailure {
    public DirectoryExecutionException(String message) { super(com.dec.lite.runtime.RuntimeErrorCode.VALIDATION, null, message); }
}
