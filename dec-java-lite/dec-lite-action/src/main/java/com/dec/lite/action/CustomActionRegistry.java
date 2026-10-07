package com.dec.lite.action;

import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomActionRegistry {
    private final Map<String, CustomAction> actions = new ConcurrentHashMap<>();
    private volatile boolean frozen;

    public synchronized CustomActionRegistry register(CustomAction action) {
        if (frozen) throw new IllegalStateException("Custom Action registry is frozen");
        if (action == null || action.name().isBlank()) throw new IllegalArgumentException("Custom Action with name is required");
        if (actions.putIfAbsent(action.name(), action) != null) throw new IllegalArgumentException("duplicate Custom Action: " + action.name());
        return this;
    }
    public synchronized CustomActionRegistry freeze() { frozen = true; return this; }
    public CustomAction require(ActionDefinition definition) {
        CustomAction action = actions.get(definition.customType());
        if (action == null || !action.supports(definition)) throw new IllegalStateException("Custom Action is not registered: " + definition.customType());
        action.validate(definition);
        return action;
    }
    public void validate(Collection<ActionDefinition> definitions) {
        for (ActionDefinition definition : definitions) if (!definition.isRuleView()) require(definition);
    }
}
