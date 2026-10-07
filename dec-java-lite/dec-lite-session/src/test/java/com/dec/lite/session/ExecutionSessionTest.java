package com.dec.lite.session;

import com.dec.lite.action.*;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionSessionTest {
    @Test void nestedRequiredScopesReuseOneRouteAndCommitOnce() {
        TransactionCoordinator transactions = new TransactionCoordinator(TransactionPolicy.REQUIRED, null);
        Resource resource = new Resource();
        try (var outer = transactions.begin()) {
            Object first = transactions.enlist("data1", () -> resource, Object.class);
            try (var inner = transactions.begin()) {
                Object second = transactions.enlist("data1", () -> { throw new AssertionError("must reuse"); }, Object.class);
                assertSame(first, second);
                inner.commit();
            }
            assertEquals(0, resource.commits);
            outer.commit();
        }
        assertEquals(1, resource.commits);
        assertEquals(1, resource.closes);
        assertEquals(TransactionCoordinator.State.COMMITTED, transactions.state());
        assertTrue(transactions.events().stream().anyMatch(event -> event.outcome().equals("REUSED")));
    }
    @Test void crossRouteFailureRollsBackAndCompensatesExternalEffects() {
        TransactionCoordinator transactions = new TransactionCoordinator(TransactionPolicy.REQUIRED, null);
        Resource resource = new Resource(); List<String> compensation = new ArrayList<>();
        try (var scope = transactions.begin()) {
            transactions.enlist("data1", () -> resource, Object.class);
            transactions.recordExternalEffect("notify", () -> compensation.add("notify"));
            RuntimeFailure failure = assertThrows(RuntimeFailure.class,
                    () -> transactions.enlist("data2", Resource::new, Object.class));
            assertEquals(RuntimeErrorCode.TRANSACTION, failure.code());
        }
        assertEquals(1, resource.rollbacks);
        assertEquals(1, resource.closes);
        assertEquals(List.of("notify"), compensation);
        assertTrue(transactions.events().stream().anyMatch(event -> event.outcome().equals("REJECTED")));
    }
    @Test void failedCommitIsReportedAsInDoubtRatherThanSuccessfulRollback() {
        TransactionCoordinator transactions = new TransactionCoordinator(TransactionPolicy.REQUIRED, null);
        Resource resource = new Resource() {
            @Override public void commit() { throw new IllegalStateException("commit uncertain"); }
        };
        try (var scope = transactions.begin()) {
            transactions.enlist("data1", () -> resource, Object.class);
            RuntimeFailure failure = assertThrows(RuntimeFailure.class, scope::commit);
            assertEquals(RuntimeErrorCode.TRANSACTION, failure.code());
        }
        assertEquals(TransactionCoordinator.State.FAILED, transactions.state());
        assertEquals(1, resource.rollbacks);
        assertEquals(1, resource.closes);
        assertTrue(transactions.events().stream().anyMatch(event -> event.outcome().equals("ROLLBACK_ATTEMPTED")));
        assertFalse(transactions.events().stream().anyMatch(event -> event.outcome().equals("ROLLED_BACK")));
    }
    @Test void standaloneActionUsesOneSessionAndSystemWritePolicy() throws Exception {
        DecProject project = project();
        InformationEngine information = new InformationEngine(new InformationParser().parse(project));
        CustomActionRegistry custom = new CustomActionRegistry().register(new CustomAction() {
            public String name() { return "setStatus"; }
            public void execute(ActionExecutionContext context) {
                context.model().write("status", 2L, new MutationSet());
                context.produce("value", 2L);
            }
        });
        ActionRuntime runtime = new ActionRuntime(new RuleViewRegistry(), custom);
        CountingTransaction legacy = new CountingTransaction();
        try (ExecutionSession session = new ExecutionSession(information, runtime,
                new ModelContext(Map.of("status", 0L)), Map.of(), Instant.MAX, null,
                TransactionPolicy.REQUIRED, legacy, SystemAccessPolicy.compile(project))) {
            List<String> callbacks = new ArrayList<>();
            session.onWorkflow(new WorkflowCallback() {
                public void before(ActionDefinition action, ExecutionSession session) { callbacks.add("before"); }
                public void after(ActionDefinition action, ActionResult result, ExecutionSession session) { callbacks.add("after"); }
                public void failed(ActionDefinition action, SessionError error, ExecutionSession session) { callbacks.add("failed"); }
            });
            session.consumeProduced((action, values, scope) -> callbacks.add("consume:" + values.get("value")));
            assertEquals(ActionStatus.SUCCESS, session.execute(action("order")).status());
            assertEquals(2L, session.model().read("status"));
            assertEquals(1, legacy.commits);
            assertEquals(List.of("before", "consume:2", "after"), callbacks);
            ActionResult denied = session.execute(action("payment"));
            assertEquals(ActionStatus.FAILED, denied.status());
            assertEquals(RuntimeErrorCode.ACCESS_DENIED, denied.failureCode());
            assertEquals(2L, session.model().read("status"));
            assertEquals(1, legacy.rollbacks);
            assertEquals(RuntimeErrorCode.ACCESS_DENIED, session.errors().get(0).code());
            assertEquals("failed", callbacks.get(callbacks.size() - 1));
        }
    }
    @Test void parallelSessionsDoNotShareModelCacheTransactionOrTrace() throws Exception {
        InformationEngine shared = new InformationEngine(new InformationParser().parse(project()));
        ActionRuntime runtime = new ActionRuntime(new RuleViewRegistry(), new CustomActionRegistry());
        ModelContext initial = new ModelContext(Map.of("status", 0L));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> new ExecutionSession(shared, runtime, initial, Map.of(), Instant.MAX, null,
                    TransactionPolicy.NONE, null));
            var two = pool.submit(() -> new ExecutionSession(shared, runtime, initial, Map.of(), Instant.MAX, null,
                    TransactionPolicy.NONE, null));
            try (ExecutionSession first = one.get(); ExecutionSession second = two.get()) {
                assertNotEquals(first.id(), second.id());
                assertNotSame(first.information(), second.information());
                assertNotSame(first.model(), second.model());
                assertNotSame(first.transactions(), second.transactions());
                first.model().write("status", 1L, new MutationSet());
                assertEquals(0L, second.model().read("status"));
                assertEquals(0L, initial.read("status"));
                first.trace("ONLY_FIRST", "x", null);
                assertFalse(second.trace().stream().anyMatch(event -> event.phase().equals("ONLY_FIRST")));
            }
        }
    }
    private static ActionDefinition action(String system) {
        return new ActionDefinition("ACT-" + system, "setStatus", "ordered", system, null, "setStatus",
                List.of(), null, null, List.of(), Map.of(), "fail-fast", Path.of("test.yaml"));
    }
    private static DecProject project() throws Exception {
        return new DecYamlParser().parse(Path.of(ExecutionSessionTest.class.getClassLoader().getResource("mix").toURI()));
    }
    private static class Resource implements TransactionResource {
        final Object handle = new Object(); int commits; int rollbacks; int closes;
        public Object handle() { return handle; }
        public void commit() { commits++; }
        public void rollback() { rollbacks++; }
        public void close() { closes++; }
    }
    private static final class CountingTransaction implements ActionTransaction {
        int commits; int rollbacks;
        public void commit() { commits++; }
        public void rollback() { rollbacks++; }
    }
}
