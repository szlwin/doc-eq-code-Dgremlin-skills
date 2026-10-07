package com.dec.lite.directory;

public final class DirectoryCompilationException extends com.dec.lite.runtime.RuntimeFailure {
    public DirectoryCompilationException(String message) { super(com.dec.lite.runtime.RuntimeErrorCode.COMPILATION, null, message); }
}
