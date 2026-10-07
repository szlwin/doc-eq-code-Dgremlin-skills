package com.dec.lite.session;

import com.dec.lite.action.ActionDefinition;
import java.util.Map;

/** Consumes verified Produce data in the same transaction Scope. */
@FunctionalInterface
public interface ProducedDataConsumer {
    void consume(ActionDefinition action, Map<String, Object> produced, ExecutionSession session);
}
