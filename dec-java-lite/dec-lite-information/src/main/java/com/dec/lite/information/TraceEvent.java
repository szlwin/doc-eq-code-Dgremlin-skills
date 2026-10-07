package com.dec.lite.information;

/** One deterministic step in an Information recognition trace. */
public record TraceEvent(InformationKey key, String operation, RecognitionResult.Status status, String detail) {
    public TraceEvent {
        if (key == null) throw new IllegalArgumentException("trace key is required");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("trace operation is required");
        if (status == null) throw new IllegalArgumentException("trace status is required");
    }
}
