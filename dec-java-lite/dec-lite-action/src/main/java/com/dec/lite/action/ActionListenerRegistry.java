package com.dec.lite.action;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable Business ID index with at most one listener per flow. Request state is never stored here. */
public final class ActionListenerRegistry {
    private final Map<String, ActionListener> listeners;
    private ActionListenerRegistry(Map<String, ActionListener> listeners) {
        this.listeners = Map.copyOf(listeners);
    }
    public static ActionListenerRegistry empty() { return from(List.of()); }
    public static ActionListenerRegistry from(Collection<? extends ActionListener> providers) {
        Objects.requireNonNull(providers, "listeners");
        Map<String, ActionListener> index = new LinkedHashMap<>();
        for (ActionListener listener : providers) {
            if (listener == null || listener.businessId() == null || listener.businessId().isBlank())
                throw new IllegalArgumentException("Listener Business Design ID is required");
            if (index.putIfAbsent(listener.businessId(), listener) != null)
                throw new IllegalArgumentException("Only one listener is allowed per Business: " + listener.businessId());
        }
        return new ActionListenerRegistry(index);
    }
    public java.util.Optional<ActionListener> listenerFor(ActionDefinition action) {
        return java.util.Optional.ofNullable(action.businessId() == null ? null : listeners.get(action.businessId()));
    }
    /** Fail startup when a configured ID is absent, instead of silently ignoring a typo. */
    public void validate(Collection<String> businessIds) {
        for (String id : listeners.keySet())
            if (!businessIds.contains(id)) throw new IllegalArgumentException("Listener references unknown Business Design ID: " + id);
    }
}
