package com.dec.lite.parser;

import java.nio.file.Path;

/** Stable validation failure from either canonical DEC frontend. */
public class DecParseException extends com.dec.lite.runtime.RuntimeFailure {

    public DecParseException(Path source, String message) {
        super(com.dec.lite.runtime.RuntimeErrorCode.VALIDATION, null, source, source + ": " + message, null);
    }

    public DecParseException(Path source, String message, Throwable cause) {
        super(com.dec.lite.runtime.RuntimeErrorCode.VALIDATION, null, source, source + ": " + message, cause);
    }
}
