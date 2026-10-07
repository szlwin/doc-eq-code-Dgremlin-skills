package com.dec.lite.information;

import java.nio.file.Path;

/** Canonical System-owned Information after structural parsing. */
public record InformationDefinition(
        String designId,
        InformationKey key,
        String viewRef,
        String ruleRef,
        String ruleData,
        String changeData,
        String expression,
        Path source) {
    public InformationDefinition {
        if (designId == null || designId.isBlank()) throw new IllegalArgumentException("Information designId is required");
        if (key == null) throw new IllegalArgumentException("Information key is required");
        if (source == null) throw new IllegalArgumentException("Information source is required");
    }

    public InformationKind kind() {
        if (ruleRef != null) return InformationKind.RULE_VIEW_ATOMIC;
        if (ruleData != null) return InformationKind.MODEL_EXPRESSION_ATOMIC;
        return InformationKind.COMPOSITE;
    }

    public boolean materializable() { return ruleData != null && changeData != null; }
}
