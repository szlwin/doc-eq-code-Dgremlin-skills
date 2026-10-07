package com.dec.lite.runtime;

import java.nio.file.Path;

public class RuntimeFailure extends RuntimeException {
    private final RuntimeErrorCode code;
    private final String entityKey;
    private final Path source;
    public RuntimeFailure(RuntimeErrorCode code, String entityKey, Path source, String message, Throwable cause) {
        super(message, cause);
        this.code = code; this.entityKey = entityKey; this.source = source;
    }
    public RuntimeFailure(RuntimeErrorCode code, String entityKey, String message) {
        this(code, entityKey, null, message, null);
    }
    public RuntimeErrorCode code() { return code; }
    public String entityKey() { return entityKey; }
    public Path source() { return source; }
}
