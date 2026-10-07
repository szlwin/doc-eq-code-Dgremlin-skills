package com.dec.lite.action;

import com.dec.lite.information.InformationKey;

import java.util.Objects;

public record ProduceDefinition(String id, String ref, String type, InformationKey informationRef, boolean required,
                                 int multiplicity, String scope, String source) {
    public ProduceDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Produce id is required");
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("Produce ref is required");
        if (multiplicity < 1) throw new IllegalArgumentException("Produce multiplicity must be positive");
        scope = scope == null || scope.isBlank() ? "action" : scope;
        source = source == null ? "canonical" : source;
    }
    public ProduceDefinition(String id, String ref, InformationKey informationRef, boolean required,
                             int multiplicity, String scope, String source) {
        this(id, ref, null, informationRef, required, multiplicity, scope, source);
    }
}
