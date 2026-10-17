package com.dec.lite.directory;

import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.ActionListenerRegistry;
import com.dec.lite.action.ActionViewBinding;
import com.dec.lite.action.DataSourceAdapterRegistry;
import com.dec.lite.action.DataSourceSelection;
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
    private final java.util.List<com.dec.lite.action.ActionDefinition> actions;
    private final String defaultDataSource;
    private final java.util.Set<RuleViewKey> recognitionRules;

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
        recognitionRules = information.definitions().values().stream().filter(d -> d.ruleRef() != null)
                .map(d -> new RuleViewKey(d.key().system(), d.viewRef(), d.ruleRef())).collect(java.util.stream.Collectors.toSet());
        var actionsByBusiness = new BusinessActionParser().parseByBusiness(project);
        defaultDataSource = RuleViewCompiler.businessDataSource(project);
        actions = actionsByBusiness.values().stream().flatMap(java.util.List::stream).toList();
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
        if (request.viewBinding() != null) validateViewBinding(request.viewBinding());
        RuleViewRegistry rules = new RuleViewCompiler().bind(compiledRules, request.ruleAdapter(), actions, defaultDataSource, recognitionRules).freeze();
        WorkflowTransaction.Binding binding = request.transaction().bind();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(information, rules));
        try (ExecutionSession session = new ExecutionSession(engine,
                new ActionRuntime(rules, request.customActions(), listeners, clock, request.viewBinding(), request.ruleAdapter()), request.model(), request.payload(),
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

    private void validateViewBinding(ActionViewBinding binding) {
        for (DirectoryDefinition definition : directory.graph().directories().values()) {
            if (!definition.modelRef().equals(binding.viewRef()))
                throw new IllegalArgumentException("Flow View binding " + binding.viewRef()
                        + " conflicts with Directory " + definition.name() + " modelRef=" + definition.modelRef());
        }
    }

    /** Resolve and validate one execution's implementation choices without modifying compiled design. */
    public DataSourceSelection selectDataSources(DataSourceAdapterRegistry registry, Map<String, String> selection) {
        DataSourceSelection selected = Objects.requireNonNull(registry, "DataSource registry").select(selection);
        selected.validate(compiledRules.values(), actions, defaultDataSource, recognitionRules);
        return selected;
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
        RuleViewRegistry rules = new RuleViewCompiler().bind(compiledRules, ruleAdapter, actions, defaultDataSource, recognitionRules).freeze();
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
        DirectoryDefinition root = directory.graph().directories().get(directory.graph().root());
        ActionViewBinding viewBinding = new ActionViewBinding(root.modelRef(),
                Objects.requireNonNull(value, "flow View").getClass(), codec::fromModel);
        return execute(new RunRequest(currentDirectory, target, back, codec.toModel(value), payload,
                ruleAdapter, customActions, transaction, deadline, viewBinding),
                (model, execution, scope) -> completionHandler.complete(codec.fromModel(model), execution, scope)).value();
    }

    public <T> T advance(T value, String currentDirectory, String target, boolean back,
                         Map<String, Object> payload, DataSourceAdapterRegistry registry, Map<String, String> selection,
                         CustomActionRegistry customActions, WorkflowTransaction transaction, Instant deadline,
                         ModelCodec<T> codec, CompletionHandler<T> completionHandler) {
        return advance(value, currentDirectory, target, back, payload, selectDataSources(registry, selection),
                customActions, transaction, deadline, codec, completionHandler);
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
                             CustomActionRegistry customActions, WorkflowTransaction transaction, Instant deadline,
                             ActionViewBinding viewBinding) {
        public RunRequest(String currentDirectory, String target, boolean back, ModelContext model,
                          Map<String, Object> payload, DataOperationAdapter ruleAdapter,
                          CustomActionRegistry customActions, WorkflowTransaction transaction, Instant deadline) {
            this(currentDirectory, target, back, model, payload, ruleAdapter, customActions, transaction, deadline, null);
        }
        public RunRequest withView(ActionViewBinding binding) {
            return new RunRequest(currentDirectory, target, back, model, payload, ruleAdapter,
                    customActions, transaction, deadline, Objects.requireNonNull(binding, "View binding"));
        }
        public RunRequest withDataSources(DataSourceAdapterRegistry registry, Map<String, String> selection) {
            return new RunRequest(currentDirectory, target, back, model, payload, registry.select(selection),
                    customActions, transaction, deadline, viewBinding);
        }
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
