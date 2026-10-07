package com.dec.lite.session;

import java.time.Instant;

public record SessionTraceEvent(Instant at, String phase, String entityKey, String detail) {
    public SessionTraceEvent(String phase, String entityKey, String detail) { this(Instant.now(), phase, entityKey, detail); }
}
