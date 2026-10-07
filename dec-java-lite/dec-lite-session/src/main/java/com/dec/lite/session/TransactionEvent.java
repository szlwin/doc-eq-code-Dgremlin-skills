package com.dec.lite.session;

import java.time.Instant;

public record TransactionEvent(Instant at, String route, String outcome, String detail) {
    public TransactionEvent(String route, String outcome, String detail) { this(Instant.now(), route, outcome, detail); }
}
