package com.dec.lite.action;

public interface CustomAction {
    String name();
    default boolean supports(ActionDefinition definition) { return name().equals(definition.customType()); }
    default void validate(ActionDefinition definition) { }
    void execute(ActionExecutionContext context);
}
