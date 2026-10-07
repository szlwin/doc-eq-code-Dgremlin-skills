package com.dec.lite.directory;

import com.dec.lite.action.ActionExecutionContext;
import com.dec.lite.action.ActionDefinition;
import com.dec.lite.action.ActionResult;
import com.dec.lite.action.ActionStatus;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;
import com.dec.lite.session.SessionError;
import com.dec.lite.information.InformationKey;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.MutationSet;
import com.dec.lite.information.RecognitionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Directory state machine bound to one ExecutionSession and one Scope per path. */
public final class DirectoryEngine {
    private final DirectoryGraph graph;
    private final PathPlanner planner = new PathPlanner();
    private final BackPlanner backPlanner = new BackPlanner();
    private final DependencyGate dependencies = new DependencyGate();

    public DirectoryEngine(DirectoryGraph graph) { this.graph = graph; }
    public DirectoryGraph graph() { return graph; }

    public DirectoryExecutionResult executeTo(String target, DirectoryExecutionContext context) {
        String start = context.currentDirectory() == null ? graph.root() : context.currentDirectory();
        PathPlan plan;
        try { plan = planner.plan(graph, context.currentDirectory(), target); }
        catch (RuntimeException failure) {
            context.trace(start, DirectoryStage.FAILED, failure.getMessage());
            SessionError error = context.recordError(RuntimeErrorCode.VALIDATION, target, null, failure);
            return new DirectoryExecutionResult(false, context.currentDirectory(), failure.getMessage(), null,
                    new MutationSet(), context.trace(), error);
        }
        ModelContext working = context.model().copy();
        MutationSet mutations = new MutationSet();
        context.trace(start, DirectoryStage.PLANNED, plan.directories().toString());
        String completedTarget = target;
        PathPlan completedPlan = plan;
        try (var scope = context.session().begin()) {
            int first = context.currentDirectory() == null ? 0 : 1;
            for (int index = first; index < plan.directories().size(); index++) {
                String directory = plan.directories().get(index);
                if (index > 0 && "case".equals(plan.edgeKinds().get(index - 1))) {
                    String selected = classify(plan.directories().get(index - 1), working, context);
                    if (!directory.equals(selected)) throw new RuntimeFailure(RuntimeErrorCode.CLASSIFICATION, directory, "requested case " + directory + " differs from classified case " + selected);
                }
                executeDirectory(graph.directories().get(directory), working, context, mutations);
            }
            if (graph.directories().get(target).result()) {
                String selected = classify(target, working, context);
                executeDirectory(graph.directories().get(selected), working, context, mutations);
                List<String> nodes = new ArrayList<>(plan.directories()); nodes.add(selected);
                List<String> kinds = new ArrayList<>(plan.edgeKinds()); kinds.add("case");
                completedTarget = selected;
                completedPlan = new PathPlan(plan.from(), selected, nodes, kinds);
            }
            scope.commit();
            context.trace(completedTarget, DirectoryStage.TRANSACTION, "commit");
            context.model().replaceFrom(working);
            context.information().invalidate(mutations);
            context.setCurrentDirectory(completedTarget);
            return new DirectoryExecutionResult(true, completedTarget, null, completedPlan, mutations, context.trace(), null);
        } catch (RuntimeException failure) {
            RuntimeErrorCode code = failure instanceof RuntimeFailure typed ? typed.code() : RuntimeErrorCode.VALIDATION;
            context.trace(start, DirectoryStage.TRANSACTION, "rollback state=" + context.session().transactions().state());
            context.trace(start, DirectoryStage.FAILED, failure.getMessage());
            SessionError error = context.recordError(code, target,
                    graph.directories().get(target) == null ? null : graph.directories().get(target).source(), failure);
            context.notifyActionFailure(error);
            return new DirectoryExecutionResult(false, context.currentDirectory(), failure.getMessage(), completedPlan, mutations, context.trace(), error);
        }
    }

    public DirectoryExecutionResult backTo(String target, DirectoryExecutionContext context) {
        String from = context.currentDirectory();
        if (from == null) throw new DirectoryExecutionException("Back requires current Directory");
        BackPlan plan;
        try { plan = backPlanner.plan(graph, from, target); }
        catch (RuntimeException failure) {
            context.trace(from, DirectoryStage.FAILED, failure.getMessage());
            SessionError error = context.recordError(RuntimeErrorCode.VALIDATION, target, null, failure);
            return new DirectoryExecutionResult(false, from, failure.getMessage(), null, new MutationSet(), context.trace(), error);
        }
        ModelContext working = context.model().copy();
        MutationSet mutations = new MutationSet();
        context.trace(from, DirectoryStage.PLANNED, "Back " + plan.directories());
        try (var scope = context.session().begin()) {
            for (BackEdge edge : plan.actions()) {
                context.trace(edge.from(), DirectoryStage.BACK, edge.from() + " -> " + edge.to());
                runActions(edge.actions(), working, context, mutations, edge.from());
            }
            DirectoryDefinition destination = graph.directories().get(target);
            RecognitionResult restored = context.information().evaluate(destination.informationRef(), working);
            if (restored.status() != RecognitionResult.Status.TRUE && destination.changeInformation() != null) {
                MutationSet change = context.session().materialize(destination.changeInformation(), working);
                change.mutations().forEach(mutations::add);
            }
            verify(destination.informationRef(), working, context, target);
            scope.commit();
            context.trace(target, DirectoryStage.TRANSACTION, "commit Back");
            context.model().replaceFrom(working);
            context.information().invalidate(mutations);
            context.setCurrentDirectory(target);
            return new DirectoryExecutionResult(true, target, null,
                    new PathPlan(from, target, plan.directories(), java.util.Collections.nCopies(plan.directories().size() - 1, "back")),
                    mutations, context.trace(), null);
        } catch (RuntimeException failure) {
            RuntimeErrorCode code = failure instanceof RuntimeFailure typed ? typed.code() : RuntimeErrorCode.ACTION;
            context.trace(from, DirectoryStage.TRANSACTION, "rollback Back state=" + context.session().transactions().state());
            context.trace(from, DirectoryStage.FAILED, failure.getMessage());
            SessionError error = context.recordError(code, target,
                    graph.directories().get(target) == null ? null : graph.directories().get(target).source(), failure);
            context.notifyActionFailure(error);
            return new DirectoryExecutionResult(false, from, failure.getMessage(),
                    new PathPlan(from, target, plan.directories(), java.util.Collections.nCopies(plan.directories().size() - 1, "back")),
                    mutations, context.trace(), error);
        }
    }

    private void executeDirectory(DirectoryDefinition directory, ModelContext model,
                                  DirectoryExecutionContext context, MutationSet mutations) {
        if (Instant.now().isAfter(context.deadline())) throw new RuntimeFailure(RuntimeErrorCode.VALIDATION, directory.id(), directory.source(), "Directory deadline exceeded: " + directory.name(), null);
        context.trace(directory.name(), DirectoryStage.CHECKING_DEPENDENCIES, directory.dependencies().toString());
        try { dependencies.verify(directory.dependencies(), context.information(), model); }
        catch (DirectoryExecutionException failure) { throw new RuntimeFailure(RuntimeErrorCode.DEPENDENCY, directory.id(), directory.source(), failure.getMessage(), failure); }
        context.trace(directory.name(), DirectoryStage.EXECUTING_ACTIONS, Integer.toString(directory.actions().size()));
        runActions(directory.actions(), model, context, mutations, directory.name());
        if (directory.changeInformation() != null) {
            context.trace(directory.name(), DirectoryStage.APPLYING_CHANGE, directory.changeInformation().toString());
            MutationSet change = context.session().materialize(directory.changeInformation(), model);
            change.mutations().forEach(mutations::add);
            verify(directory.changeInformation(), model, context, directory.name());
        }
        context.trace(directory.name(), DirectoryStage.VERIFYING_INFORMATION, directory.informationRef().toString());
        verify(directory.informationRef(), model, context, directory.name());
        context.trace(directory.name(), DirectoryStage.COMPLETED, directory.informationRef().toString());
    }

    private static void runActions(List<ActionDefinition> actions, ModelContext model,
                                   DirectoryExecutionContext context, MutationSet mutations, String directory) {
        ActionExecutionContext actionContext = new ActionExecutionContext(context.information(), model, context.payload(),
                context.deadline(), context.transaction(), false, false, context.session().accessPolicy());
        for (ActionDefinition action : actions) {
            context.executing(action);
            try {
                context.session().beforeAction(action);
                ActionResult result = context.actions().execute(action, actionContext);
                result.mutations().mutations().forEach(mutations::add);
                context.trace(directory, DirectoryStage.EXECUTING_ACTIONS,
                        result.status() + " evidence=" + result.evidence() + " trace=" + result.trace());
                if (!result.producedData().isEmpty()) context.trace(directory, DirectoryStage.VERIFYING_PRODUCE, result.producedData().keySet().toString());
                if (result.status() != ActionStatus.SUCCESS) {
                    RuntimeErrorCode code = result.status() == ActionStatus.SKIPPED ? RuntimeErrorCode.DEPENDENCY :
                            result.failureCode() == null ? RuntimeErrorCode.ACTION : result.failureCode();
                    throw new RuntimeFailure(code, action.id(), action.source(), "Action failed in " + directory + ": " + result.error() + " " + result.diagnostics(), null);
                }
                context.session().afterAction(action, result);
                context.actionCompleted();
            } catch (RuntimeFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new RuntimeFailure(RuntimeErrorCode.ACTION, action.id(), action.source(),
                        "Action failed in " + directory + ": " + failure.getMessage(), failure);
            }
        }
    }

    private static void verify(InformationKey key, ModelContext model, DirectoryExecutionContext context, String directory) {
        RecognitionResult result = context.information().evaluate(key, model);
        context.trace(directory, DirectoryStage.VERIFYING_INFORMATION, key + "=" + result.status() + " evidence=" + result.evidence());
        if (result.status() != RecognitionResult.Status.TRUE) throw new RuntimeFailure(RuntimeErrorCode.VALIDATION, directory, null, "Directory Information is not TRUE: " + directory + " -> " + key + " (" + result.status() + ")", null);
    }

    private String classify(String resultDirectory, ModelContext model, DirectoryExecutionContext context) {
        List<String> matches = new ArrayList<>();
        for (CaseEdge edge : graph.caseEdges()) {
            if (!edge.parent().equals(resultDirectory)) continue;
            RecognitionResult result = context.information().evaluate(edge.informationRef(), model);
            context.trace(resultDirectory, DirectoryStage.CLASSIFYING, edge.target() + "=" + result.status());
            if (result.status() == RecognitionResult.Status.TRUE) matches.add(edge.target());
            else if (result.status() == RecognitionResult.Status.ERROR || result.status() == RecognitionResult.Status.UNRESOLVED)
                throw new RuntimeFailure(RuntimeErrorCode.CLASSIFICATION, resultDirectory, "case Information is " + result.status() + ": " + edge.informationRef());
        }
        if (matches.size() != 1) throw new RuntimeFailure(RuntimeErrorCode.CLASSIFICATION, resultDirectory, "result case must have exactly one match: " + resultDirectory + " matches=" + matches);
        return matches.get(0);
    }
}
