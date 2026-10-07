package com.dec.lite.session;

import com.dec.lite.action.ActionTransaction;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Session-owned, thread-confined transaction boundary; only one physical route is atomic. */
public final class TransactionCoordinator implements ActionTransaction, AutoCloseable {
    public enum State { IDLE, ACTIVE, COMMITTED, ROLLED_BACK, FAILED, CLOSED }
    private final TransactionPolicy policy;
    private final ActionTransaction delegate;
    private final Map<String, TransactionResource> resources = new LinkedHashMap<>();
    private final List<TransactionEvent> events = new ArrayList<>();
    private final List<ExternalEffect> externalEffects = new ArrayList<>();
    private State state = State.IDLE;
    private Thread owner;
    private String activeRoute;
    private int depth;
    private boolean rollbackOnly;

    public TransactionCoordinator(TransactionPolicy policy, ActionTransaction delegate) {
        this.policy = policy == null ? TransactionPolicy.REQUIRED : policy;
        this.delegate = delegate;
    }
    public synchronized Scope begin() {
        if (state == State.CLOSED) throw failure("session transaction is closed", null);
        if (depth == 0) {
            resources.clear(); externalEffects.clear(); activeRoute = null; rollbackOnly = false;
            owner = Thread.currentThread(); state = State.ACTIVE;
            events.add(new TransactionEvent(null, "BEGIN", policy.name()));
        } else checkOwner();
        depth++;
        events.add(new TransactionEvent(activeRoute, depth == 1 ? "OPEN" : "JOIN", "depth=" + depth));
        return new Scope(depth);
    }
    public synchronized <T> T enlist(String route, Supplier<? extends TransactionResource> factory, Class<T> type) {
        checkActive();
        if (route == null || route.isBlank()) throw failure("resource route is required", null);
        if (policy == TransactionPolicy.NONE) throw failure("transaction policy NONE cannot enlist a transactional resource", null);
        if (delegate != null) throw failure("legacy transaction and managed resource cannot share an atomic Scope", null);
        if (activeRoute != null && !activeRoute.equals(route)) {
            events.add(new TransactionEvent(route, "REJECTED", "cross-route atomic transaction is unsupported; active=" + activeRoute));
            throw failure("cross-route atomic transaction is unsupported: " + activeRoute + " -> " + route, null);
        }
        TransactionResource resource = resources.get(route);
        if (resource == null) {
            resource = factory.get();
            if (resource == null || resource.handle() == null) throw failure("resource factory returned no handle for " + route, null);
            resources.put(route, resource); activeRoute = route;
            events.add(new TransactionEvent(route, "ENLISTED", resource.handle().getClass().getSimpleName()));
        } else events.add(new TransactionEvent(route, "REUSED", "same transaction handle"));
        if (!type.isInstance(resource.handle())) throw failure("resource type mismatch for " + route, null);
        return type.cast(resource.handle());
    }
    public synchronized void recordExternalEffect(String key, Runnable compensation) {
        checkActive();
        externalEffects.add(new ExternalEffect(key, compensation));
        events.add(new TransactionEvent(activeRoute, "EXTERNAL_EFFECT", key + (compensation == null ? " without compensation" : " with compensation")));
    }
    public synchronized void markRollbackOnly(String reason) {
        checkActive(); rollbackOnly = true;
        events.add(new TransactionEvent(activeRoute, "ROLLBACK_ONLY", reason));
    }
    @Override public synchronized Object resource(String name) {
        TransactionResource resource = resources.get(name);
        return resource == null ? delegate == null ? null : delegate.resource(name) : resource.handle();
    }
    /** Adapters may request rollback, but only the owning Scope may commit. */
    @Override public void rollback() { markRollbackOnly("requested by participant"); }
    @Override public void commit() { throw failure("only the owning transaction Scope may commit", null); }
    public synchronized State state() { return state; }
    public TransactionPolicy policy() { return policy; }
    public ActionTransaction legacyTransaction() { return delegate; }
    public synchronized List<TransactionEvent> events() { return List.copyOf(events); }
    private void checkOwner() {
        if (owner != Thread.currentThread()) throw failure("transaction used from another thread", null);
    }
    private void checkActive() {
        checkOwner(); if (state != State.ACTIVE || depth < 1) throw failure("transaction scope is not active", null);
    }
    private RuntimeFailure failure(String message, Throwable cause) {
        return new RuntimeFailure(RuntimeErrorCode.TRANSACTION, activeRoute, null, message, cause);
    }
    private void commitScope(int level) {
        synchronized (this) {
            checkActive(); if (depth != level) throw failure("transaction scopes must close in stack order", null);
            depth--;
            if (depth > 0) { events.add(new TransactionEvent(activeRoute, "JOIN_COMPLETE", "depth=" + depth)); return; }
            if (rollbackOnly) {
                rollbackResources("rollback-only");
                closeResources();
                throw failure("transaction is rollback-only", null);
            }
            try {
                if (policy == TransactionPolicy.REQUIRED) {
                    for (Map.Entry<String, TransactionResource> entry : resources.entrySet()) {
                        entry.getValue().commit(); events.add(new TransactionEvent(entry.getKey(), "COMMITTED", null));
                    }
                    if (delegate != null) { delegate.commit(); events.add(new TransactionEvent("legacy", "COMMITTED", null)); }
                }
                state = State.COMMITTED;
            } catch (RuntimeException failure) {
                events.add(new TransactionEvent(activeRoute, "COMMIT_FAILED", failure.getMessage()));
                for (Map.Entry<String, TransactionResource> entry : resources.entrySet()) {
                    try { entry.getValue().rollback(); events.add(new TransactionEvent(entry.getKey(), "ROLLBACK_ATTEMPTED", "commit outcome unknown")); }
                    catch (RuntimeException rollbackFailure) { events.add(new TransactionEvent(entry.getKey(), "ROLLBACK_FAILED", rollbackFailure.getMessage())); }
                }
                if (delegate != null) {
                    try { delegate.rollback(); events.add(new TransactionEvent("legacy", "ROLLBACK_ATTEMPTED", "commit outcome unknown")); }
                    catch (RuntimeException rollbackFailure) { events.add(new TransactionEvent("legacy", "ROLLBACK_FAILED", rollbackFailure.getMessage())); }
                }
                for (ExternalEffect effect : externalEffects)
                    events.add(new TransactionEvent(activeRoute, "EXTERNAL_IN_DOUBT", effect.key()));
                state = State.FAILED;
                throw failure("transaction commit failed", failure);
            } finally { closeResources(); }
        }
    }
    private void rollbackScope(int level) {
        synchronized (this) {
            checkActive(); if (depth != level) throw failure("transaction scopes must close in stack order", null);
            depth--; rollbackOnly = true;
            if (depth > 0) { events.add(new TransactionEvent(activeRoute, "JOIN_ROLLBACK_ONLY", "depth=" + depth)); return; }
            rollbackResources("scope rollback"); closeResources();
        }
    }
    private void rollbackResources(String reason) {
        List<Map.Entry<String, TransactionResource>> entries = new ArrayList<>(resources.entrySet());
        java.util.Collections.reverse(entries);
        boolean failed = false;
        if (policy == TransactionPolicy.REQUIRED) {
            for (Map.Entry<String, TransactionResource> entry : entries) {
                try { entry.getValue().rollback(); events.add(new TransactionEvent(entry.getKey(), "ROLLED_BACK", reason)); }
                catch (RuntimeException exception) { failed = true; events.add(new TransactionEvent(entry.getKey(), "ROLLBACK_FAILED", exception.getMessage())); }
            }
            if (delegate != null) {
                try { delegate.rollback(); events.add(new TransactionEvent("legacy", "ROLLED_BACK", reason)); }
                catch (RuntimeException exception) { failed = true; events.add(new TransactionEvent("legacy", "ROLLBACK_FAILED", exception.getMessage())); }
            }
        }
        for (int index = externalEffects.size() - 1; index >= 0; index--) {
            ExternalEffect effect = externalEffects.get(index);
            if (effect.compensation() == null) { failed = true; events.add(new TransactionEvent(activeRoute, "UNCOMPENSATED", effect.key())); continue; }
            try { effect.compensation().run(); events.add(new TransactionEvent(activeRoute, "COMPENSATED", effect.key())); }
            catch (RuntimeException exception) { failed = true; events.add(new TransactionEvent(activeRoute, "COMPENSATION_FAILED", effect.key() + ": " + exception.getMessage())); }
        }
        state = failed ? State.FAILED : State.ROLLED_BACK;
    }
    private void closeResources() {
        for (Map.Entry<String, TransactionResource> entry : resources.entrySet()) {
            try { entry.getValue().close(); events.add(new TransactionEvent(entry.getKey(), "CLOSED", null)); }
            catch (RuntimeException exception) { events.add(new TransactionEvent(entry.getKey(), "CLOSE_FAILED", exception.getMessage())); }
        }
        resources.clear(); externalEffects.clear(); activeRoute = null; owner = null;
    }
    @Override public synchronized void close() {
        if (state == State.CLOSED) return;
        if (depth > 0) {
            checkOwner(); depth = 0; rollbackResources("session close"); closeResources();
        }
        state = State.CLOSED;
        events.add(new TransactionEvent(null, "SESSION_CLOSED", null));
    }
    private record ExternalEffect(String key, Runnable compensation) { }
    public final class Scope implements AutoCloseable {
        private final int level;
        private boolean complete;
        private Scope(int level) { this.level = level; }
        public ActionTransaction transaction() { return TransactionCoordinator.this; }
        public void commit() { if (complete) throw failure("transaction Scope already completed", null); commitScope(level); complete = true; }
        public void rollback() { if (complete) return; rollbackScope(level); complete = true; }
        @Override public void close() { if (!complete && state() == State.ACTIVE) rollback(); }
    }
}
