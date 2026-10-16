package com.dec.lite.action;

import com.dec.lite.information.MutationSet;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;
import com.dec.lite.runtime.RuntimeErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ActionRuntime {
    private final java.time.Clock clock;
    private final RuleViewActionInvoker ruleViews;
    private final CustomActionRegistry customActions;
    private final ProduceVerifier produceVerifier;
    private final ActionListenerRegistry listeners;

    public ActionRuntime(RuleViewRegistry ruleViews, CustomActionRegistry customActions) {
        this(ruleViews, customActions, java.time.Clock.systemUTC());
    }

    public ActionRuntime(RuleViewRegistry ruleViews, CustomActionRegistry customActions, java.time.Clock clock) {
        this(ruleViews, customActions, ActionListenerRegistry.empty(), clock);
    }

    public ActionRuntime(RuleViewRegistry ruleViews, CustomActionRegistry customActions, ActionListenerRegistry listeners) {
        this(ruleViews, customActions, listeners, java.time.Clock.systemUTC());
    }

    public ActionRuntime(RuleViewRegistry ruleViews, CustomActionRegistry customActions,
                         ActionListenerRegistry listeners, java.time.Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.listeners = java.util.Objects.requireNonNull(listeners, "listeners");
        this.ruleViews = new RuleViewActionInvoker(ruleViews);
        this.customActions = customActions;
        this.produceVerifier = new ProduceVerifier();
    }

    public ActionResult execute(ActionDefinition action, ActionExecutionContext context) {
        List<ActionTraceEvent> trace = new ArrayList<>();
        MutationSet mutations = new MutationSet();
        try {
            checkDeadline(action, context);
            for (var dependency : action.dependencies()) {
                RecognitionResult result = context.informationEngine().evaluate(dependency, context.model());
                if (result.status() != RecognitionResult.Status.TRUE) {
                    trace.add(new ActionTraceEvent(action.name(), "precondition", ActionStatus.SKIPPED, dependency + "=" + result.status()));
                    return ActionResult.skipped("Action dependency is not TRUE: " + dependency + " (" + result.status() + ")");
                }
            }
            // All model changes remain private until Rule, Produce and target Information pass.
            ActionExecutionContext execution = new ActionExecutionContext(context.informationEngine(),
                    context.model().copyWithGuard(path -> context.accessPolicy().checkWrite(action.systemRef(), path)),
                    context.payload(), context.deadline(), context.transaction(),
                    context.transactionOwner(), context.applyDirectoryChange(), context.accessPolicy());
            checkDeadline(action, context);
            ActionListenerContext listenerContext = new ActionListenerContext(action, execution, mutations);
            notifyListener(action, listenerContext, execution, null, trace);
            RecognitionResult ruleResult = null;
            if (action.isRuleView()) {
                ruleResult = ruleViews.invoke(action, execution, mutations);
                if (ruleResult.status() != RecognitionResult.Status.TRUE) {
                    trace.add(new ActionTraceEvent(action.name(), "invoke", ActionStatus.FAILED, ruleResult.status().name()));
                    RuntimeException rollbackFailure = rollback(context, action, trace);
                    RuntimeErrorCode code = ruleResult.errors().stream().anyMatch(error -> error.startsWith("ACCESS_DENIED")) ? RuntimeErrorCode.ACCESS_DENIED : RuntimeErrorCode.ACTION;
                    List<String> diagnostics = new ArrayList<>(ruleResult.errors());
                    if (rollbackFailure != null) diagnostics.add("rollback failed: " + rollbackFailure);
                    return ActionResult.failed("RuleView Action failed: " + action.name() + " (" + ruleResult.status() + "): " + String.join("; ", diagnostics), mutations, diagnostics, trace, code);
                }
            } else {
                CustomAction custom = customActions.require(action);
                custom.execute(execution);
            }
            checkDeadline(action, context);
            if (context.applyDirectoryChange() && action.changeInformation() != null) {
                MutationSet materialized = context.informationEngine().materialize(action.changeInformation(), execution.model());
                materialized.mutations().forEach(mutations::add);
            }
            trace.add(new ActionTraceEvent(action.name(), "invoke", ActionStatus.SUCCESS, null));
            ActionResult result = ActionResult.success(execution.produced(), mutations,
                    ruleResult == null ? List.of(action.name()) : ruleResult.evidence(),
                    trace);
            produceVerifier.verify(action, result, execution);
            notifyListener(action, listenerContext, execution, result, trace);
            result = ActionResult.success(execution.produced(), mutations, result.evidence(), trace);
            // Listener writes must not bypass the declared Produce postconditions.
            if (listeners.listenerFor(action).isPresent()) produceVerifier.verify(action, result, execution);
            if (action.targetInformation() != null && action.produces().stream().noneMatch(produce -> action.targetInformation().equals(produce.informationRef()))) {
                // The target gate is evaluated by the Directory runtime after optional Change in P5.
                trace.add(new ActionTraceEvent(action.name(), "directory-target-deferred", ActionStatus.SUCCESS, action.targetInformation().toString()));
            }
            checkDeadline(action, context);
            context.model().replaceFrom(execution.model());
            if (!mutations.isEmpty()) context.informationEngine().invalidate(mutations);
            if (context.transactionOwner()) context.transaction().commit();
            return result;
        } catch (RuntimeException exception) {
            RuntimeException rollbackFailure = rollback(context, action, trace);
            trace.add(new ActionTraceEvent(action.name(), "invoke", ActionStatus.FAILED, exception.getMessage()));
            RuntimeErrorCode code = exception instanceof ProduceExecutionException ? RuntimeErrorCode.PRODUCE :
                    exception instanceof com.dec.lite.runtime.RuntimeFailure failure ? failure.code() :
                    exception instanceof SecurityException ? RuntimeErrorCode.ACCESS_DENIED : RuntimeErrorCode.ACTION;
            List<String> diagnostics = new ArrayList<>();
            diagnostics.add(exception.toString());
            if (rollbackFailure != null) diagnostics.add("rollback failed: " + rollbackFailure);
            return ActionResult.failed(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    mutations, diagnostics, trace, code);
        }
    }

    private void notifyListener(ActionDefinition action, ActionListenerContext listenerContext,
                                 ActionExecutionContext execution, ActionResult result, List<ActionTraceEvent> trace) {
        String phase = result == null ? "before" : "after";
        ActionListener listener = listeners.listenerFor(action).orElse(null);
        if (listener == null) return;
        checkDeadline(action, execution);
        try {
            if (result == null) listener.before(listenerContext); else listener.after(listenerContext, result);
        } catch (RuntimeException failure) {
            RuntimeErrorCode code = failure instanceof SecurityException ? RuntimeErrorCode.ACCESS_DENIED : RuntimeErrorCode.ACTION;
            throw new com.dec.lite.runtime.RuntimeFailure(code, action.id(), action.source(),
                    phase + " listener " + listener.getClass().getName() + " failed: " + failure.getMessage(), failure);
        }
        checkDeadline(action, execution);
        trace.add(new ActionTraceEvent(action.name(), "listener-" + phase, ActionStatus.SUCCESS, listener.getClass().getName()));
    }

    private void checkDeadline(ActionDefinition action, ActionExecutionContext context) {
        if (!clock.instant().isBefore(context.deadline()))
            throw new com.dec.lite.runtime.RuntimeFailure(RuntimeErrorCode.TIMEOUT, action.id(),
                    "Action deadline exceeded: " + action.name());
    }

    private static RuntimeException rollback(ActionExecutionContext context, ActionDefinition action,
                                             List<ActionTraceEvent> trace) {
        if (!context.transactionOwner()) return null;
        try {
            context.transaction().rollback();
            return null;
        } catch (RuntimeException failure) {
            trace.add(new ActionTraceEvent(action.name(), "rollback-failed", ActionStatus.FAILED, failure.toString()));
            return failure;
        }
    }

    public ActionResult execute(ActionDefinition action, com.dec.lite.information.InformationEngine engine,
                                com.dec.lite.information.ModelContext model) {
        return execute(action, new ActionExecutionContext(engine, model, Map.of(), Instant.MAX));
    }
}
