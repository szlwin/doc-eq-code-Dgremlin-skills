package com.dec.lite.session;

import com.dec.lite.action.ActionDefinition;
import com.dec.lite.action.ActionResult;

/** New core SPI; callbacks run inside the Action Scope before commit. */
public interface WorkflowCallback {
    default void before(ActionDefinition action, ExecutionSession session) { }
    default void after(ActionDefinition action, ActionResult result, ExecutionSession session) { }
    default void failed(ActionDefinition action, SessionError error, ExecutionSession session) { }
}
