package com.dec.lite.directory;

import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.ActionListenerRegistry;
import com.dec.lite.action.BusinessActionParser;
import com.dec.lite.action.CompiledRuleView;
import com.dec.lite.information.RuleViewKey;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.TransactionPolicy;
import com.dec.lite.information.TransactionScope;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.action.DataOperationAdapter;
import com.dec.lite.action.RuleViewCompiler;
import com.dec.lite.information.InformationCompilation;
import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.InformationEngineContext;
import com.dec.lite.information.InformationParser;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.RuleViewRegistry;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.model.DecProject;

import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Reusable compiled runtime for one design version; adapters, models and transactions are bound per request. */
public final class DirectoryWorkflowRunner {
    private final Map<RuleViewKey, CompiledRuleView> compiledRules;
    private final java.time.Clock clock;
    private final InformationCompilation information;
    private final DirectoryEngine directory;
    private final ActionListenerRegistry listeners;

    public DirectoryWorkflowRunner(DecProject project, CustomActionRegistry registeredActions) {
        this(project, registeredActions, java.time.Clock.systemUTC());
    }

    public DirectoryWorkflowRunner(DecProject project, CustomActionRegistry registeredActions, java.time.Clock clock) {
        this(project, registeredActions, ActionListenerRegistry.empty(), clock);
    }

    public DirectoryWorkflowRunner(DecProject project, CustomActionRegistry registeredActions, ActionListenerRegistry listeners) {
        this(project, registeredActions, listeners, java.time.Clock.systemUTC());
    }

    public DirectoryWorkflowRunner(DecProject project, CustomActionRegistry registeredActions,
                                   ActionListenerRegistry listeners, java.time.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
        Objects.requireNonNull(project, "project");
        compiledRules = new RuleViewCompiler().compile(project);
        information = new InformationParser().parse(project);
        var actionsByBusiness = new BusinessActionParser().parseByBusiness(project);
        var actions = actionsByBusiness.values().stream().flatMap(java.util.List::stream).toList();
        listeners.validate(actionsByBusiness.keySet());
        directory = new DirectoryEngine(new DirectoryGraphCompiler().compileReady(
                project, information, Objects.requireNonNull(registeredActions, "registeredActions"),
                actions, compiledRules), clock);
    }

    public RunResult run(RunRequest request) {
        return execute(request, (model, execution, transaction) -> model).run();
    }

    private <T> Completed<T> execute(RunRequest request, ModelCompletion<T> completion) {
        Objects.requireNonNull(request, "request");
        WorkflowTransaction.Binding binding = request.transaction().bind();
        RuleViewRegistry rules = new RuleViewCompiler().bind(compiledRules, request.ruleAdapter()).freeze();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(information, rules));
        try (ExecutionSession session = new ExecutionSession(engine,
                new ActionRuntime(rules, request.customActions(), listeners, clock), request.model(), request.payload(),
                request.deadline(), request.currentDirectory(),
                request.transaction().mode() == WorkflowTransaction.Mode.EXTERNAL ? TransactionPolicy.EXTERNAL : TransactionPolicy.REQUIRED,
                binding.participant());
             var scope = session.begin()) {
            binding.enlist(session.transactions());
            DirectoryExecutionContext context = new DirectoryExecutionContext(session);
            DirectoryExecutionResult result = request.back()
                    ? directory.backTo(request.target(), context) : directory.executeTo(request.target(), context);
            if (!result.success()) throw new DirectoryWorkflowException(request.target(), result);
            // Engine's nested scope has only joined. The owning scope remains active through completion.
            checkDeadline(request);
            T value = completion.complete(context.model(), result, session.transactions());
            checkDeadline(request);
            scope.commit();
            result = new DirectoryExecutionResult(result.success(), result.currentDirectory(), result.error(),
                    result.plan(), result.mutations(), context.trace(), result.errorDetail());
            return new Completed<>(value, new RunResult(result, context.model().copy(),
                    request.transaction().mode() == WorkflowTransaction.Mode.EXTERNAL
                            ? TransactionOutcome.EXTERNAL_PENDING : TransactionOutcome.COMMITTED));
        }
    }

    private void checkDeadline(RunRequest request) {
        if (!clock.instant().isBefore(request.deadline()))
            throw new com.dec.lite.runtime.RuntimeFailure(com.dec.lite.runtime.RuntimeErrorCode.TIMEOUT,
                    request.target(), "Workflow deadline exceeded before commit: " + request.target());
    }

    /**
     * Recover a persisted stable state from design Information, without executing Actions.
     * Result nodes are transient classification points. Stable predicates must be mutually exclusive
     * and decidable: zero/multiple matches, ERROR and UNRESOLVED all fail closed.
     * Callers distinguish a new entity from an existing entity using their repository, not this method.
     */
    public String recognizeDirectory(ModelContext model, DataOperationAdapter ruleAdapter) {
        RuleViewRegistry rules = new RuleViewCompiler().bind(compiledRules, ruleAdapter).freeze();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(information, rules));
        Map<String, RecognitionResult.Status> states = new LinkedHashMap<>();
        directory.graph().directories().values().stream().filter(node -> !node.result())
                .sorted(java.util.Comparator.comparing(DirectoryDefinition::name))
                .forEach(node -> states.put(node.name(), engine.evaluate(node.informationRef(), model).status()));
        var matches = states.entrySet().stream()
                .filter(entry -> entry.getValue() == RecognitionResult.Status.TRUE)
                .map(Map.Entry::getKey).toList();
        if (matches.size() != 1 || states.containsValue(RecognitionResult.Status.ERROR)
                || states.containsValue(RecognitionResult.Status.UNRESOLVED))
            throw new DirectoryExecutionException("Cannot recognize a unique stable Directory: " + states);
        return matches.get(0);
    }

    /** Validate a reloaded persisted model against the same design used for execution. */
    public void assertDirectoryMatches(ModelContext model, String expected, DataOperationAdapter ruleAdapter) {
        String actual = recognizeDirectory(model, ruleAdapter);
        if (!Objects.equals(expected, actual))
            throw new DirectoryExecutionException("Persisted Directory mismatch: expected " + expected + ", found " + actual);
    }

    /**
     * Optional API command policy: one execution edge, or one explicit Back edge through case ownership.
     * Unlike executeTo/advance, this rejects repeated targets and multi-step paths before side effects.
     */
    public void requireSingleStep(String current, String target, boolean back) {
        DirectoryGraph graph = directory.graph();
        boolean allowed;
        if (back) {
            allowed = current != null && !current.equals(target)
                    && new BackPlanner().plan(graph, current, target).actions().size() == 1;
        } else {
            allowed = current == null ? graph.root().equals(target)
                    : !current.equals(target) && graph.executionEdges().stream()
                    .anyMatch(edge -> edge.child().equals(current) && edge.parent().equals(target));
        }
        if (!allowed) throw new DirectoryExecutionException("Not a single " + (back ? "Back" : "execution")
                + " step: " + current + " -> " + target);
    }

    /**
     * Execute Actions and application completion inside one owning transaction scope.
     * Runtime-managed mode returns only after physical commit; external mode returns a provisional
     * result whose durability is controlled by the caller. Completion must use the same transaction.
     */
    public <T> T advance(T value, String currentDirectory, String target, boolean back,
                         Map<String, Object> payload, DataOperationAdapter ruleAdapter,
                         CustomActionRegistry customActions, WorkflowTransaction transaction, Instant deadline,
                         ModelCodec<T> codec, CompletionHandler<T> completionHandler) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(completionHandler, "completionHandler");
        return execute(new RunRequest(currentDirectory, target, back, codec.toModel(value), payload,
                ruleAdapter, customActions, transaction, deadline),
                (model, execution, scope) -> completionHandler.complete(codec.fromModel(model), execution, scope)).value();
    }

    public interface ModelCodec<T> {
        ModelContext toModel(T value);
        T fromModel(ModelContext model);
    }

    @FunctionalInterface
    public interface CompletionHandler<T> {
        T complete(T completed, DirectoryExecutionResult execution, TransactionScope transaction);
    }

    public record RunRequest(String currentDirectory, String target, boolean back, ModelContext model,
                             Map<String, Object> payload, DataOperationAdapter ruleAdapter,
                             CustomActionRegistry customActions, WorkflowTransaction transaction, Instant deadline) {
        public RunRequest {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(model, "model");
            payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
            Objects.requireNonNull(ruleAdapter, "ruleAdapter");
            Objects.requireNonNull(customActions, "customActions");
            Objects.requireNonNull(deadline, "deadline");
            Objects.requireNonNull(transaction, "An explicit WorkflowTransaction mode is required");
        }
    }

    @FunctionalInterface
    private interface ModelCompletion<T> {
        T complete(ModelContext model, DirectoryExecutionResult execution, TransactionScope transaction);
    }

    public enum TransactionOutcome { COMMITTED, EXTERNAL_PENDING }
    public record RunResult(DirectoryExecutionResult execution, ModelContext model, TransactionOutcome transactionOutcome) { }
    private record Completed<T>(T value, RunResult run) { }
}
