package com.dec.lite.directory;

import com.dec.lite.action.ActionTransaction;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;
import com.dec.lite.session.TransactionCoordinator;
import com.dec.lite.session.TransactionResource;

import java.util.Objects;
import java.util.function.Supplier;

/** Explicit transaction ownership for a complete workflow, including application completion. */
public sealed interface WorkflowTransaction {
    enum Mode { EXTERNAL, RUNTIME_MANAGED }
    Mode mode();
    Binding bind();

    /** Resolve the current caller-owned transaction on every invocation, never at bean construction. */
    static WorkflowTransaction externallyManaged(Supplier<? extends ExternalTransaction> current) {
        return new External(Objects.requireNonNull(current, "current"));
    }

    /** The factory must open a new physical transaction; all writes must use its enlisted handle. */
    static WorkflowTransaction runtimeManaged(String route, Supplier<? extends TransactionResource> factory) {
        if (route == null || route.isBlank()) throw new IllegalArgumentException("transaction route is required");
        return new Managed(route, Objects.requireNonNull(factory, "factory"));
    }

    /** External owners expose participation only: Runtime cannot physically commit or close them. */
    interface ExternalTransaction {
        boolean isActive();
        boolean isRollbackOnly();
        void markRollbackOnly();
        default Object resource(String name) { return null; }
    }

    record External(Supplier<? extends ExternalTransaction> current) implements WorkflowTransaction {
        public External { Objects.requireNonNull(current, "current"); }
        public Mode mode() { return Mode.EXTERNAL; }
        public Binding bind() {
            ExternalTransaction external = Objects.requireNonNull(current.get(), "external transaction");
            Thread owner = Thread.currentThread();
            ActionTransaction participant = new ActionTransaction() {
                private void requireActive() {
                    if (Thread.currentThread() != owner || !external.isActive())
                        throw new RuntimeFailure(RuntimeErrorCode.TRANSACTION, null, "An active external write transaction is required");
                }
                public void commit() {
                    requireActive();
                    if (external.isRollbackOnly())
                        throw new RuntimeFailure(RuntimeErrorCode.TRANSACTION, null, "External transaction is rollback-only");
                }
                public void rollback() { requireActive(); external.markRollbackOnly(); }
                public Object resource(String name) { requireActive(); return external.resource(name); }
            };
            participant.commit(); // validate participation before any workflow side effect; no physical commit
            return new Binding(participant, null, null);
        }
    }

    record Managed(String route, Supplier<? extends TransactionResource> factory) implements WorkflowTransaction {
        public Managed {
            if (route == null || route.isBlank()) throw new IllegalArgumentException("transaction route is required");
            Objects.requireNonNull(factory, "factory");
        }
        public Mode mode() { return Mode.RUNTIME_MANAGED; }
        public Binding bind() { return new Binding(null, route, factory); }
    }

    /** Runner internal binding; raw ActionTransaction is deliberately not an application entrypoint. */
    record Binding(ActionTransaction participant, String route, Supplier<? extends TransactionResource> factory) {
        void enlist(TransactionCoordinator coordinator) {
            if (factory != null) coordinator.enlist(route, factory, Object.class);
        }
    }
}
