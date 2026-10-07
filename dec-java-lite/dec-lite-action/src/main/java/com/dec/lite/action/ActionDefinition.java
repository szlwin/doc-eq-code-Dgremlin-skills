package com.dec.lite.action;

import com.dec.lite.information.InformationKey;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ActionDefinition(String id, String name, String ownerDirectory, String systemRef, String ruleRef,
                               String customType, List<InformationKey> dependencies, InformationKey targetInformation,
                               InformationKey changeInformation,
                               List<ProduceDefinition> produces, Map<String, Object> payload,
                               String failurePolicy, Path source) {
    public ActionDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Action id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Action name is required");
        if ((ruleRef == null) == (customType == null)) throw new IllegalArgumentException("Action must define exactly one of ruleRef or customType");
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        produces = List.copyOf(produces == null ? List.of() : produces);
        payload = Map.copyOf(payload == null ? Map.of() : payload);
        failurePolicy = failurePolicy == null || failurePolicy.isBlank() ? "fail-fast" : failurePolicy;
        if (source == null) throw new IllegalArgumentException("Action source is required");
    }
    public ActionDefinition(String id, String name, String ownerDirectory, String systemRef, String ruleRef,
                            String customType, List<InformationKey> dependencies, InformationKey targetInformation,
                            List<ProduceDefinition> produces, Map<String, Object> payload,
                            String failurePolicy, java.nio.file.Path source) {
        this(id, name, ownerDirectory, systemRef, ruleRef, customType, dependencies, targetInformation, null,
                produces, payload, failurePolicy, source);
    }
    public boolean isRuleView() { return ruleRef != null; }
}
