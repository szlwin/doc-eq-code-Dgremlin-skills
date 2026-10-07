package com.dec.lite.session;

import com.dec.lite.action.*;
import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.InformationKey;
import com.dec.lite.information.MutationSet;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit per-request runtime owner; no thread-local or global Session. */
public final class ExecutionSession implements AutoCloseable {
    private final String id = UUID.randomUUID().toString();
    private final InformationEngine information;
    private final ActionRuntime actions;
    private final ModelContext model;
    private final Map<String, Object> payload;
    private final Instant deadline;
    private final TransactionCoordinator transactions;
    private final SystemAccessPolicy accessPolicy;
    private final String compiledDigest;
    private final List<SessionTraceEvent> trace = new ArrayList<>();
    private final List<SessionError> errors = new ArrayList<>();
    private final List<WorkflowCallback> callbacks = new ArrayList<>();
    private final List<ProducedDataConsumer> consumers = new ArrayList<>();
    private String currentDirectory;
    private boolean closed;
    private int transactionTraceCursor;

    public ExecutionSession(InformationEngine information, ActionRuntime actions, ModelContext initialModel,
                            Map<String, Object> payload, Instant deadline, String currentDirectory,
                            TransactionPolicy policy, ActionTransaction legacyTransaction) {
        this(information, actions, initialModel, payload, deadline, currentDirectory, policy, legacyTransaction,
                SystemAccessPolicy.allowAll());
    }
    public ExecutionSession(InformationEngine information, ActionRuntime actions, ModelContext initialModel,
                            Map<String, Object> payload, Instant deadline, String currentDirectory,
                            TransactionPolicy policy, ActionTransaction legacyTransaction, SystemAccessPolicy accessPolicy) {
        this(information, actions, initialModel, payload, deadline, currentDirectory, policy, legacyTransaction, accessPolicy, null);
    }
    public ExecutionSession(InformationEngine information, ActionRuntime actions, ModelContext initialModel,
                            Map<String, Object> payload, Instant deadline, String currentDirectory,
                            TransactionPolicy policy, ActionTransaction legacyTransaction, SystemAccessPolicy accessPolicy,
                            String compiledDigest) {
        if (information == null || actions == null || initialModel == null) throw new IllegalArgumentException("Information, Action and model are required");
        this.information = new InformationEngine(information.context());
        this.actions = actions; this.model = initialModel.copy();
        this.payload = Map.copyOf(payload == null ? Map.of() : payload);
        this.deadline = deadline == null ? Instant.MAX : deadline;
        this.currentDirectory = currentDirectory;
        this.transactions = new TransactionCoordinator(policy, legacyTransaction);
        this.accessPolicy = accessPolicy == null ? SystemAccessPolicy.allowAll() : accessPolicy;
        this.compiledDigest = compiledDigest;
        trace("SESSION_OPEN", id, "modelVersion=" + model.version() + " compiledDigest=" + contextVersion());
    }
    public String id() { return id; }
    public synchronized String contextVersion() { return compiledDigest == null ? model.version() : compiledDigest; }
    public InformationEngine information() { return information; }
    public ActionRuntime actions() { return actions; }
    public ModelContext model() { return model; }
    public Map<String, Object> payload() { return payload; }
    public Instant deadline() { return deadline; }
    public TransactionCoordinator transactions() { return transactions; }
    public SystemAccessPolicy accessPolicy() { return accessPolicy; }
    public MutationSet materialize(InformationKey key, ModelContext working) {
        if (transactions.state() != TransactionCoordinator.State.ACTIVE) throw new IllegalStateException("materialization requires an active transaction Scope");
        ModelContext scoped = working.copyWithGuard(path -> accessPolicy.checkWrite(key.system(), path));
        MutationSet mutations = information.materialize(key, scoped);
        working.replaceFrom(scoped);
        return mutations;
    }
    public synchronized String currentDirectory() { return currentDirectory; }
    public synchronized void setCurrentDirectory(String value) { ensureOpen(); currentDirectory = value; trace("DIRECTORY_STATE", value, null); }
    public synchronized List<SessionTraceEvent> trace() { syncTransactions(); return List.copyOf(trace); }
    public synchronized List<SessionError> errors() { return List.copyOf(errors); }
    public synchronized void trace(String phase, String key, String detail) { trace.add(new SessionTraceEvent(phase, key, detail)); }
    private void syncTransactions() {
        List<TransactionEvent> events = transactions.events();
        while (transactionTraceCursor < events.size()) {
            TransactionEvent event = events.get(transactionTraceCursor++);
            trace.add(new SessionTraceEvent(event.at(), "TRANSACTION_" + event.outcome(), event.route(), event.detail()));
        }
    }
    public synchronized SessionError recordError(RuntimeErrorCode code, String key, Path source, String message, Throwable cause) {
        trace("ERROR", key, code + ": " + message);
        SessionError error = new SessionError(code, key, source, message, trace(), cause);
        errors.add(error); return error;
    }
    public synchronized ExecutionSession onWorkflow(WorkflowCallback callback) { ensureOpen(); callbacks.add(callback); return this; }
    public synchronized ExecutionSession consumeProduced(ProducedDataConsumer consumer) { ensureOpen(); consumers.add(consumer); return this; }
    public void beforeAction(ActionDefinition action) {
        ensureOpen();
        for (WorkflowCallback callback : List.copyOf(callbacks)) {
            try { callback.before(action, this); }
            catch (RuntimeException failure) { throw new RuntimeFailure(RuntimeErrorCode.ACTION, action.id(), action.source(), "before callback failed: " + failure.getMessage(), failure); }
        }
    }
    public void afterAction(ActionDefinition action, ActionResult result) {
        ensureOpen();
        for (ProducedDataConsumer consumer : List.copyOf(consumers)) {
            try { consumer.consume(action, result.producedData(), this); }
            catch (RuntimeException failure) { throw new RuntimeFailure(RuntimeErrorCode.PRODUCE, action.id(), action.source(), "Produce consumer failed: " + failure.getMessage(), failure); }
        }
        for (WorkflowCallback callback : List.copyOf(callbacks)) {
            try { callback.after(action, result, this); }
            catch (RuntimeException failure) { throw new RuntimeFailure(RuntimeErrorCode.ACTION, action.id(), action.source(), "after callback failed: " + failure.getMessage(), failure); }
        }
    }
    public void actionFailed(ActionDefinition action, SessionError error) {
        for (WorkflowCallback callback : List.copyOf(callbacks)) {
            try { callback.failed(action, error, this); }
            catch (RuntimeException callbackFailure) {
                recordError(RuntimeErrorCode.ACTION, action.id(), action.source(),
                        "failure callback failed: " + callbackFailure.getMessage(), callbackFailure);
            }
        }
    }
    public TransactionCoordinator.Scope begin() { ensureOpen(); return transactions.begin(); }

    /** Standalone Action entrypoint using the same transaction and model publication rules as Directory. */
    public ActionResult execute(ActionDefinition action) {
        ensureOpen(); ModelContext working = model.copy();
        try (TransactionCoordinator.Scope scope = begin()) {
            beforeAction(action);
            ActionExecutionContext context = new ActionExecutionContext(information, working, payload, deadline,
                    scope.transaction(), false, true, accessPolicy);
            ActionResult result = actions.execute(action, context);
            if (result.status() != ActionStatus.SUCCESS) {
                RuntimeErrorCode code = result.status() == ActionStatus.SKIPPED ? RuntimeErrorCode.DEPENDENCY :
                        result.failureCode() == null ? RuntimeErrorCode.ACTION : result.failureCode();
                throw new RuntimeFailure(code, action.id(), action.source(),
                        result.error() == null ? String.join("; ", result.diagnostics()) : result.error(), null);
            }
            afterAction(action, result);
            scope.commit();
            model.replaceFrom(working);
            if (!result.mutations().isEmpty()) information.invalidate(result.mutations());
            trace("ACTION_COMMITTED", action.id(), result.evidence().toString());
            return result;
        } catch (RuntimeException failure) {
            RuntimeErrorCode code = failure instanceof RuntimeFailure typed ? typed.code() : RuntimeErrorCode.ACTION;
            SessionError error = recordError(code, action.id(), action.source(), failure.getMessage(), failure);
            actionFailed(action, error);
            return ActionResult.failed(error.message(), new com.dec.lite.information.MutationSet(),
                    List.of(error.message()), List.of(), code);
        }
    }
    private synchronized void ensureOpen() { if (closed) throw new IllegalStateException("ExecutionSession is closed"); }
    @Override public synchronized void close() {
        if (closed) return;
        transactions.close(); closed = true; trace("SESSION_CLOSED", id, null);
    }
}
