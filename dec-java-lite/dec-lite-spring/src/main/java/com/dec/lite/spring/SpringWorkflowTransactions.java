package com.dec.lite.spring;

import com.dec.lite.directory.WorkflowTransaction;
import com.dec.lite.session.TransactionResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.Objects;
import java.util.function.Supplier;

/** Spring bridge with explicit ownership; no annotation or thread-local transaction is assumed silently. */
public final class SpringWorkflowTransactions {
    private SpringWorkflowTransactions() { }

    /** For an enclosing Spring @Transactional method. */
    public static WorkflowTransaction externallyManaged() {
        return externallyManaged(TransactionAspectSupport::currentTransactionStatus);
    }

    /** For TransactionTemplate, pass () -> the callback's TransactionStatus. */
    public static WorkflowTransaction externallyManaged(Supplier<? extends TransactionStatus> current) {
        Objects.requireNonNull(current, "current");
        return WorkflowTransaction.externallyManaged(() -> {
            requireWriteTransaction();
            TransactionStatus status = Objects.requireNonNull(current.get(), "Spring transaction status");
            // Activity on the thread alone is insufficient: REQUIRES_NEW suspends
            // this physical transaction while another one is active on that thread.
            var owner = new TransactionSynchronization() {
                private boolean active = true;
                public void suspend() { active = false; }
                public void resume() { active = true; }
                public void afterCompletion(int completionStatus) { active = false; }
                boolean active() { return active; }
            };
            TransactionSynchronizationManager.registerSynchronization(owner);
            return new WorkflowTransaction.ExternalTransaction() {
                public boolean isActive() {
                    return owner.active() && !status.isCompleted() && TransactionSynchronizationManager.isActualTransactionActive()
                            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                }
                public boolean isRollbackOnly() { return status.isRollbackOnly(); }
                public void markRollbackOnly() { status.setRollbackOnly(); }
                public Object resource(String name) { return TransactionSynchronizationManager.getResource(name); }
            };
        });
    }

    /** Runtime starts and completes a new Spring transaction; joining an ambient transaction is rejected. */
    public static WorkflowTransaction runtimeManaged(String route, PlatformTransactionManager manager) {
        Objects.requireNonNull(manager, "manager");
        return WorkflowTransaction.runtimeManaged(route, () -> {
            if (TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("Runtime-managed mode cannot join an external Spring transaction");
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED);
            TransactionStatus status = manager.getTransaction(definition);
            if (!status.isNewTransaction()) {
                status.setRollbackOnly();
                manager.rollback(status);
                throw new IllegalStateException("Runtime-managed mode requires a new physical Spring transaction");
            }
            return new TransactionResource() {
                public Object handle() { return status; }
                public void commit() {
                    if (status.isRollbackOnly())
                        throw new org.springframework.transaction.UnexpectedRollbackException("Runtime-managed transaction is rollback-only");
                    manager.commit(status);
                }
                public void rollback() { if (!status.isCompleted()) manager.rollback(status); }
                public void close() { if (!status.isCompleted()) manager.rollback(status); }
            };
        });
    }

    private static void requireWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("External mode requires an active Spring write transaction");
    }
}
