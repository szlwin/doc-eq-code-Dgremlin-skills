package com.dec.lite.directory;

import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.ActionDefinition;
import com.dec.lite.action.ActionTransaction;
import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.ModelContext;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.SessionError;
import com.dec.lite.session.TransactionPolicy;
import com.dec.lite.runtime.RuntimeErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DirectoryExecutionContext {
    private final ExecutionSession session;
    private final List<ExecutionTraceEvent> trace = new ArrayList<>();
    private int transactionTraceCursor;
    private ActionDefinition failingAction;

    public DirectoryExecutionContext(InformationEngine information, ActionRuntime actions, ModelContext model,
                                     Map<String, Object> payload, ActionTransaction transaction, Instant deadline,
                                     String currentDirectory) {
        this(new ExecutionSession(information, actions, model, payload, deadline, currentDirectory,
                transaction == null ? TransactionPolicy.NONE : TransactionPolicy.REQUIRED, transaction));
    }
    public DirectoryExecutionContext(ExecutionSession session) { this.session = session; }
    public ExecutionSession session() { return session; }
    public InformationEngine information() { return session.information(); }
    public ActionRuntime actions() { return session.actions(); }
    public ModelContext model() { return session.model(); }
    public Map<String, Object> payload() { return session.payload(); }
    public ActionTransaction transaction() { return session.transactions(); }
    public Instant deadline() { return session.deadline(); }
    public String currentDirectory() { return session.currentDirectory(); }
    void setCurrentDirectory(String name) { session.setCurrentDirectory(name); }
    public List<ExecutionTraceEvent> trace() {
        var events = session.transactions().events();
        while (transactionTraceCursor < events.size()) {
            var event = events.get(transactionTraceCursor++);
            trace.add(new ExecutionTraceEvent(event.route() == null ? "session" : event.route(), DirectoryStage.TRANSACTION,
                    event.outcome() + " " + event.detail()));
        }
        return List.copyOf(trace);
    }
    void trace(String directory, DirectoryStage stage, String detail) {
        trace.add(new ExecutionTraceEvent(directory, stage, detail));
        session.trace(stage.name(), directory, detail);
    }
    SessionError recordError(RuntimeErrorCode code, String key, java.nio.file.Path source, RuntimeException failure) {
        if (failure instanceof com.dec.lite.runtime.RuntimeFailure typed) {
            if (typed.entityKey() != null) key = typed.entityKey();
            if (typed.source() != null) source = typed.source();
        }
        return session.recordError(code, key, source, failure.getMessage(), failure);
    }
    void executing(ActionDefinition action) { failingAction = action; }
    void actionCompleted() { failingAction = null; }
    void notifyActionFailure(SessionError error) {
        if (failingAction != null) {
            session.actionFailed(failingAction, error);
            failingAction = null;
        }
    }
}
