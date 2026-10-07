package com.dec.lite.action;

import com.dec.lite.information.MutationSet;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;

public final class RuleViewActionInvoker {
    private final RuleViewRegistry registry;
    public RuleViewActionInvoker(RuleViewRegistry registry) { this.registry = registry; }

    public RecognitionResult invoke(ActionDefinition action, ActionExecutionContext context, MutationSet mutations) {
        if (action.systemRef() == null || action.systemRef().isBlank()) throw new IllegalArgumentException("RuleView Action requires systemRef: " + action.name());
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>(context.payload());
        payload.putAll(action.payload());
        return registry.invoke(action.systemRef(), action.ruleRef(), context.model(), payload, mutations,
                context.mutableProduced(), context.transaction());
    }
}
