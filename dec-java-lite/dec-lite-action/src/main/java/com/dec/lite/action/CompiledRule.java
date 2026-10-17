package com.dec.lite.action;

import com.dec.lite.model.CanonicalValues;
import java.util.Map;

/** Immutable counterpart of RuleDefineInfo and its check/data/execute specializations. */
public record CompiledRule(String id, String name, String type, String property, String pattern,
                           String process, String dataSource, String cmd, RuleError error,
                           Map<String, Object> customer) {
    public CompiledRule {
        customer = CanonicalValues.map(customer);
        if (customer.values().stream().anyMatch(value -> !(value instanceof String)))
            throw new ActionExecutionException("Rule customer must contain flat string values (XML attributes)");
    }
    public CompiledRule(String id, String name, String type, String property, String pattern,
                        String process, String dataSource) {
        this(id, name, type, property, pattern, process, dataSource, null, null, Map.of());
    }
    public CompiledRule withDataSource(String source) {
        return new CompiledRule(id, name, type, property, pattern, process, source, cmd, error, customer);
    }
    public boolean external() {
        return java.util.Set.of("checkData", "checkDataPattern", "get", "query", "insert", "update", "delete").contains(type);
    }
    public record RuleError(String code, String message, String level) {
        public RuleError {
            if (code == null || code.isBlank() || message == null || message.isBlank())
                throw new ActionExecutionException("Rule error requires code and message");
        }
    }
}
