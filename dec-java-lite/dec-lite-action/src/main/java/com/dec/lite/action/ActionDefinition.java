package com.dec.lite.action;

import com.dec.lite.information.InformationKey;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ActionDefinition(String id, String name, String ownerDirectory, String systemRef, String ruleRef,
                               String customType, List<InformationKey> dependencies, InformationKey targetInformation,
                               InformationKey changeInformation,
                               List<ProduceDefinition> produces, Map<String, Object> payload,
                               String failurePolicy, Path source, String businessId,
                               String dataSource, String businessDataSource) {
    public ActionDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Action id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Action name is required");
        if ((ruleRef == null) == (customType == null)) throw new IllegalArgumentException("Action must define exactly one of ruleRef or customType");
        if (dataSource != null && dataSource.isBlank() || businessDataSource != null && businessDataSource.isBlank())
            throw new IllegalArgumentException("DataSource names must not be blank");
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        produces = List.copyOf(produces == null ? List.of() : produces);
        payload = Map.copyOf(payload == null ? Map.of() : payload);
        failurePolicy = failurePolicy == null || failurePolicy.isBlank() ? "fail-fast" : failurePolicy;
        if (source == null) throw new IllegalArgumentException("Action source is required");
    }
    public ActionDefinition(String id, String name, String ownerDirectory, String systemRef, String ruleRef,
                            String customType, List<InformationKey> dependencies, InformationKey targetInformation,
                            InformationKey changeInformation, List<ProduceDefinition> produces, Map<String, Object> payload,
                            String failurePolicy, Path source, String businessId) {
        this(id, name, ownerDirectory, systemRef, ruleRef, customType, dependencies, targetInformation,
                changeInformation, produces, payload, failurePolicy, source, businessId, null, null);
    }
    /** Custom Actions have no Rule/RuleView source; inherit the Business source unless overridden. */
    public String defaultDataSource() { return dataSource != null ? dataSource : businessDataSource; }
    public String resolveDataSource(String ruleSource) {
        return dataSource != null ? dataSource : ruleSource != null ? ruleSource : businessDataSource;
    }
    /** Compatibility for standalone Actions without a Business binding. */
    public ActionDefinition(String id, String name, String ownerDirectory, String systemRef, String ruleRef,
                            String customType, List<InformationKey> dependencies, InformationKey targetInformation,
                            InformationKey changeInformation, List<ProduceDefinition> produces, Map<String, Object> payload,
                            String failurePolicy, Path source) {
        this(id, name, ownerDirectory, systemRef, ruleRef, customType, dependencies, targetInformation,
                changeInformation, produces, payload, failurePolicy, source, null);
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
