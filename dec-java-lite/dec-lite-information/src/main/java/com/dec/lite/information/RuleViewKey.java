package com.dec.lite.information;

/** Stable, system-qualified identity for a RuleView invocation. */
public record RuleViewKey(String system, String viewRef, String ruleRef) {
    public RuleViewKey {
        if (system == null || system.isBlank()) throw new IllegalArgumentException("RuleView system is required");
        if (viewRef == null || viewRef.isBlank()) throw new IllegalArgumentException("RuleView viewRef is required");
        if (ruleRef == null || ruleRef.isBlank()) throw new IllegalArgumentException("RuleView ruleRef is required");
    }
}
