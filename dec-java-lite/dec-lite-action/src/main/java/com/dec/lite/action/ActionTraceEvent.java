package com.dec.lite.action;

public record ActionTraceEvent(String action, String operation, ActionStatus status, String detail) {
    public ActionTraceEvent {
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action trace name is required");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("action trace operation is required");
        if (status == null) throw new IllegalArgumentException("action trace status is required");
    }
}
