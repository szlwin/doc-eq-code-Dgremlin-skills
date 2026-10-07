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
    private final RuleViewActionInvoker ruleViews;
    private final CustomActionRegistry customActions;
    private final ProduceVerifier produceVerifier;

    public ActionRuntime(RuleViewRegistry ruleViews, CustomActionRegistry customActions) {
        this.ruleViews = new RuleViewActionInvoker(ruleViews);
        this.customActions = customActions;
        this.produceVerifier = new ProduceVerifier();
    }

    public ActionResult execute(ActionDefinition action, ActionExecutionContext context) {
        List<ActionTraceEvent> trace = new ArrayList<>();
        MutationSet mutations = new MutationSet();
        try {
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
            RecognitionResult ruleResult = null;
            if (action.isRuleView()) {
                ruleResult = ruleViews.invoke(action, execution, mutations);
                if (ruleResult.status() != RecognitionResult.Status.TRUE) {
                    trace.add(new ActionTraceEvent(action.name(), "invoke", ActionStatus.FAILED, ruleResult.status().name()));
                    if (context.transactionOwner()) try { context.transaction().rollback(); } catch (RuntimeException ignored) { }
                    RuntimeErrorCode code = ruleResult.errors().stream().anyMatch(error -> error.startsWith("ACCESS_DENIED")) ? RuntimeErrorCode.ACCESS_DENIED : RuntimeErrorCode.ACTION;
                    return ActionResult.failed("RuleView Action failed: " + action.name() + " (" + ruleResult.status() + "): " + String.join("; ", ruleResult.errors()), mutations, ruleResult.errors(), trace, code);
                }
            } else {
                CustomAction custom = customActions.require(action);
                custom.execute(execution);
            }
            if (context.applyDirectoryChange() && action.changeInformation() != null) {
                MutationSet materialized = context.informationEngine().materialize(action.changeInformation(), execution.model());
                materialized.mutations().forEach(mutations::add);
            }
            ActionResult result = ActionResult.success(execution.produced(), mutations,
                    ruleResult == null ? List.of(action.name()) : ruleResult.evidence(),
                    List.of(new ActionTraceEvent(action.name(), "invoke", ActionStatus.SUCCESS, null)));
            produceVerifier.verify(action, result, execution);
            if (action.targetInformation() != null && action.produces().stream().noneMatch(produce -> action.targetInformation().equals(produce.informationRef()))) {
                // The target gate is evaluated by the Directory runtime after optional Change in P5.
                trace.add(new ActionTraceEvent(action.name(), "directory-target-deferred", ActionStatus.SUCCESS, action.targetInformation().toString()));
            }
            context.model().replaceFrom(execution.model());
            if (!mutations.isEmpty()) context.informationEngine().invalidate(mutations);
            if (context.transactionOwner()) context.transaction().commit();
            return result;
        } catch (RuntimeException exception) {
            if (context.transactionOwner()) try { context.transaction().rollback(); } catch (RuntimeException ignored) { }
            trace.add(new ActionTraceEvent(action.name(), "invoke", ActionStatus.FAILED, exception.getMessage()));
            RuntimeErrorCode code = exception instanceof ProduceExecutionException ? RuntimeErrorCode.PRODUCE :
                    exception instanceof SecurityException ? RuntimeErrorCode.ACCESS_DENIED : RuntimeErrorCode.ACTION;
            return ActionResult.failed(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    mutations, List.of(exception.toString()), trace, code);
        }
    }

    public ActionResult execute(ActionDefinition action, com.dec.lite.information.InformationEngine engine,
                                com.dec.lite.information.ModelContext model) {
        return execute(action, new ActionExecutionContext(engine, model, Map.of(), Instant.MAX));
    }
}
