package com.dec.lite.session;

import com.dec.lite.runtime.RuntimeErrorCode;
import java.nio.file.Path;
import java.util.List;

public record SessionError(RuntimeErrorCode code, String entityKey, Path source, String message,
                           List<SessionTraceEvent> trace, Throwable cause) {
    public SessionError { trace = List.copyOf(trace); }
}
