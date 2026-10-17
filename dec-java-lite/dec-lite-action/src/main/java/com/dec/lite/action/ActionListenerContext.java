package com.dec.lite.action;

import com.dec.lite.information.MutationSet;
import java.time.Instant;
import java.util.Map;

/** Request-local view of the actual Action working model; writes retain ownership checks and mutations. */
public final class ActionListenerContext {
    private final ActionDefinition action;
    private final ActionExecutionContext execution;
    private final MutationSet mutations;
    private final ActionViewBinding viewBinding;

    ActionListenerContext(ActionDefinition action, ActionExecutionContext execution, MutationSet mutations,
                          ActionViewBinding viewBinding) {
        this.action = action; this.execution = execution; this.mutations = mutations; this.viewBinding = viewBinding;
    }
    public ActionDefinition action() { return action; }
    public String businessId() { return action.businessId(); }
    /** Fresh typed snapshot of this Action's working model, not the initial API request. */
    public <T> T view(Class<T> type) {
        if (viewBinding == null) throw new IllegalStateException("No typed flow View binding; use advance with a ModelCodec or RunRequest.withView");
        return viewBinding.read(type, execution.model());
    }
    public String viewRef() { return viewBinding == null ? null : viewBinding.viewRef(); }
    /** Detached model value; use write() to publish changes. */
    public Object read(String path) { return execution.model().copy().read(path); }
    public void write(String path, Object value) {
        execution.model().write(path, value, mutations);
        execution.informationEngine().invalidate(mutations);
    }
    public Map<String, Object> payload() { return execution.payload(); }
    public Instant deadline() { return execution.deadline(); }
}
