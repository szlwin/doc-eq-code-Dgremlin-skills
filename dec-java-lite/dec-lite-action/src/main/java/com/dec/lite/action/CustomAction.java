package com.dec.lite.action;

public interface CustomAction {
    /** Registry key; defaults to the canonical Action name when ruleRef is absent. */
    String name();
    default boolean supports(ActionDefinition definition) { return name().equals(definition.customType()); }
    default void validate(ActionDefinition definition) { }
    /** Read model/payload and publish declared outputs with context.produce; propagate failures.
     * Irreversible external effects require the application's transaction/delivery policy.
     * Implementations must bound I/O using context.deadline(); runtime checks before/after execution
     * cannot interrupt a blocked external client or undo an already delivered side effect. */
    void execute(ActionExecutionContext context);
}
