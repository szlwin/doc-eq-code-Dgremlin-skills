package com.dec.lite.spring;

import com.dec.lite.action.*;
import com.dec.lite.directory.*;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.parser.DecXmlParser;
import com.dec.lite.runtime.RuntimeFailure;
import com.dec.lite.session.TransactionCoordinator;
import com.dec.lite.session.TransactionResource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowTransactionsTest {
    enum Mode { EXTERNAL, RUNTIME_MANAGED }
    JdbcDataSource dataSource;
    JdbcTemplate jdbc;
    CountingManager manager;
    DirectoryWorkflowRunner runner;
    CustomActionRegistry custom;
    ModelContext original;
    AtomicInteger actionCalls;
    AtomicInteger completionCalls;
    AtomicInteger afterCommit;
    DataOperationAdapter adapter;

    @BeforeEach void setup() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE workflow_row(id INT PRIMARY KEY, phase VARCHAR(20), status INT, detail_status INT)");
        manager = new CountingManager(dataSource);
        custom = CustomActionRegistry.from(List.of(new CustomAction() {
            public String name() { return "smsNotify"; }
            public void execute(ActionExecutionContext context) { fail("Unexpected SMS"); }
        }));
        runner = new DirectoryWorkflowRunner(new DecXmlParser().parse(
                Path.of(getClass().getClassLoader().getResource("mix-xml").toURI())), custom);
        original = new ModelContext(Map.of("status", 0, "orderDetailList", List.of(Map.of("status", 0)), "payInfo", Map.of()));
        actionCalls = new AtomicInteger(); completionCalls = new AtomicInteger(); afterCommit = new AtomicInteger();
        adapter = (rule, invocation) -> {
            assertEquals(TransactionCoordinator.State.ACTIVE, ((TransactionCoordinator) invocation.transaction()).state());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            actionCalls.incrementAndGet();
            switch (rule.name()) {
                case "insertOrder" -> {
                    jdbc.update("INSERT INTO workflow_row VALUES (1, 'action', 1, 0)");
                    invocation.write("status", 1);
                    invocation.produce("OrderInfo", Map.of("id", 1));
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        public void afterCommit() { afterCommit.incrementAndGet(); }
                    });
                }
                case "insertOrderDetail" -> {
                    jdbc.update("UPDATE workflow_row SET detail_status = 1 WHERE id = 1");
                    invocation.write("orderDetailList[0].status", 1);
                }
                default -> throw new IllegalStateException(rule.name());
            }
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.name());
        };
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void commitsOnlyAfterFinalWritesAndVerification(Mode mode) {
        ModelContext result = inMode(mode, tx -> advance(tx, (model, execution, scope) -> {
            completionCalls.incrementAndGet();
            assertEquals(0, manager.commits);
            assertEquals(0, afterCommit.get());
            assertEquals(TransactionCoordinator.State.ACTIVE, ((TransactionCoordinator) scope).state());
            jdbc.update("UPDATE workflow_row SET phase = 'final' WHERE id = 1");
            runner.assertDirectoryMatches(readModel(), execution.currentDirectory(), adapter);
            return model;
        }));
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        assertEquals(1, completionCalls.get());
        assertEquals(1, afterCommit.get());
        assertEquals("final", jdbc.queryForObject("SELECT phase FROM workflow_row", String.class));
        assertEquals(1, ((Number) result.read("status")).intValue());
        assertEquals(0, original.read("status"));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void finalWriteFailureRollsBackActions(Mode mode) {
        assertThrows(RuntimeException.class, () -> inMode(mode, tx -> advance(tx, (model, execution, scope) -> {
            jdbc.update("INSERT INTO workflow_row VALUES (1, 'duplicate', 1, 1)");
            return model;
        })));
        assertRolledBack();
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void verificationFailureRollsBackFinalWritesAndActions(Mode mode) {
        assertThrows(DirectoryExecutionException.class, () -> inMode(mode, tx -> advance(tx, (model, execution, scope) -> {
            jdbc.update("UPDATE workflow_row SET phase = 'final', status = 2 WHERE id = 1");
            runner.assertDirectoryMatches(readModel(), execution.currentDirectory(), adapter);
            return model;
        })));
        assertRolledBack();
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void commitFailureNeverReturnsSuccess(Mode mode) {
        manager.failCommit = true;
        assertThrows(RuntimeException.class, () -> inMode(mode, tx -> advance(tx, (model, execution, scope) -> {
            completionCalls.incrementAndGet();
            jdbc.update("UPDATE workflow_row SET phase = 'final' WHERE id = 1");
            return model;
        })));
        assertEquals(1, completionCalls.get());
        assertEquals(1, manager.commits);
        assertEquals(1, manager.rollbacks); // injected failure occurs BEFORE the physical commit
        assertEquals(0, rows());
        assertEquals(0, afterCommit.get());
        assertEquals(0, original.read("status"));
    }

    @Test void swallowedExternalCompletionFailureStillMarksOwnerRollbackOnly() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, () -> advance(SpringWorkflowTransactions.externallyManaged(() -> status),
                    (model, execution, scope) -> { throw new IllegalStateException("final write failed"); }));
            assertTrue(status.isRollbackOnly());
            assertEquals(0, manager.commits);
        });
        assertRolledBack();
    }

    @Test void externalModeRejectsMissingReadOnlyCompletedAndRollbackOnlyTransactions() {
        assertThrows(IllegalStateException.class, () -> advance(SpringWorkflowTransactions.externallyManaged(), passthrough()));
        TransactionTemplate readOnly = new TransactionTemplate(manager);
        readOnly.setReadOnly(true);
        readOnly.executeWithoutResult(status -> assertThrows(IllegalStateException.class,
                () -> advance(SpringWorkflowTransactions.externallyManaged(() -> status), passthrough())));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            assertThrows(RuntimeFailure.class, () -> advance(SpringWorkflowTransactions.externallyManaged(() -> status), passthrough()));
        });
        assertEquals(0, actionCalls.get());
        assertEquals(0, rows());
    }

    @Test void runtimeModeRejectsJoiningExternalTransaction() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, () -> advance(SpringWorkflowTransactions.runtimeManaged("data1", manager), passthrough()));
            assertFalse(status.isRollbackOnly());
        });
        assertEquals(0, actionCalls.get());
    }

    @Test void externalRunReportsPendingUntilOwnerCommits() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var result = runner.run(new DirectoryWorkflowRunner.RunRequest(null, "ordered", false, original,
                    Map.of(), adapter, custom, SpringWorkflowTransactions.externallyManaged(() -> status), Instant.MAX));
            assertEquals(DirectoryWorkflowRunner.TransactionOutcome.EXTERNAL_PENDING, result.transactionOutcome());
            assertEquals(0, manager.commits);
            assertTrue(result.execution().trace().stream().anyMatch(event -> event.detail().startsWith("EXTERNAL_PENDING")));
            assertFalse(result.execution().trace().stream().anyMatch(event -> event.detail().startsWith("COMMITTED")));
            status.setRollbackOnly();
        });
        assertRolledBack();
    }

    @Test void runtimeRunReportsCommittedAndActionFailureSkipsCompletion() {
        var result = runner.run(new DirectoryWorkflowRunner.RunRequest(null, "ordered", false, original,
                Map.of(), adapter, custom, SpringWorkflowTransactions.runtimeManaged("data1", manager), Instant.MAX));
        assertEquals(DirectoryWorkflowRunner.TransactionOutcome.COMMITTED, result.transactionOutcome());
        assertEquals(1, manager.commits);
        assertThrows(DirectoryWorkflowException.class, () -> advance(SpringWorkflowTransactions.runtimeManaged("data1", manager),
                (model, execution, scope) -> { completionCalls.incrementAndGet(); return model; })); // duplicate insert
        assertEquals(0, completionCalls.get());
        assertEquals(1, manager.rollbacks);
    }

    @Test void nullModeFailsBeforeActions() {
        assertThrows(NullPointerException.class, () -> advance(null, passthrough()));
        assertEquals(0, actionCalls.get());
    }

    @Test void runtimeRollbackOnlyCannotBeReportedAsCommitted() {
        assertThrows(RuntimeFailure.class, () -> advance(SpringWorkflowTransactions.runtimeManaged("data1", manager),
                (model, execution, scope) -> {
                    ((org.springframework.transaction.TransactionStatus) scope.resource("data1")).setRollbackOnly();
                    return model;
                }));
        assertRolledBack();
    }

    @Test void externalRollbackOnlyAtCompletionIsRejected() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThrows(RuntimeFailure.class, () -> advance(SpringWorkflowTransactions.externallyManaged(() -> status),
                    (model, execution, scope) -> { status.setRollbackOnly(); return model; }));
        });
        assertRolledBack();
    }

    @Test void completedExternalStatusCannotJoinAnotherTransaction() {
        var previous = new java.util.concurrent.atomic.AtomicReference<org.springframework.transaction.TransactionStatus>();
        new TransactionTemplate(manager).executeWithoutResult(previous::set);
        new TransactionTemplate(manager).executeWithoutResult(status -> assertThrows(RuntimeFailure.class,
                () -> advance(SpringWorkflowTransactions.externallyManaged(previous::get), passthrough())));
        assertEquals(0, actionCalls.get());
    }

    @Test void genericManagedResourceIsSharedAndClosedEvenWhenCommitOutcomeIsUnknown() throws Exception {
        var connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        AtomicInteger rollbackAttempts = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        WorkflowTransaction tx = WorkflowTransaction.runtimeManaged("jdbc", () -> new TransactionResource() {
            public Object handle() { return connection; }
            public void commit() {
                try { connection.commit(); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
                throw new IllegalStateException("simulated lost commit acknowledgement");
            }
            public void rollback() {
                rollbackAttempts.incrementAndGet();
                try { connection.rollback(); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            }
            public void close() {
                closed.incrementAndGet();
                try { connection.close(); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            }
        });
        adapter = (rule, invocation) -> {
            assertSame(connection, invocation.transaction().resource("jdbc"));
            assertThrows(RuntimeFailure.class, () -> invocation.transaction().commit());
            if (rule.name().equals("insertOrder")) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO workflow_row VALUES (1, 'action', 1, 1)");
                } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
                invocation.write("status", 1);
                invocation.produce("OrderInfo", Map.of("id", 1));
            } else invocation.write("orderDetailList[0].status", 1);
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.name());
        };
        RuntimeFailure failure = assertThrows(RuntimeFailure.class, () -> advance(tx, (model, execution, scope) -> {
            assertSame(connection, scope.resource("jdbc"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE workflow_row SET phase = 'final' WHERE id = 1");
            } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            return model;
        }));
        assertTrue(failure.getMessage().contains("outcome is unknown"));
        assertEquals(1, rollbackAttempts.get());
        assertEquals(1, closed.get());
        assertTrue(connection.isClosed());
        assertEquals("final", jdbc.queryForObject("SELECT phase FROM workflow_row", String.class));
        assertEquals(0, original.read("status")); // no successful model publication to the caller
    }

    private ModelContext inMode(Mode mode, Function<WorkflowTransaction, ModelContext> work) {
        if (mode == Mode.RUNTIME_MANAGED) return work.apply(SpringWorkflowTransactions.runtimeManaged("data1", manager));
        return new TransactionTemplate(manager).execute(status -> work.apply(SpringWorkflowTransactions.externallyManaged(() -> status)));
    }
    private ModelContext advance(WorkflowTransaction tx, DirectoryWorkflowRunner.CompletionHandler<ModelContext> completion) {
        return runner.advance(original, null, "ordered", false, Map.of(), adapter, custom, tx, Instant.MAX,
                new DirectoryWorkflowRunner.ModelCodec<>() {
                    public ModelContext toModel(ModelContext value) { return value; }
                    public ModelContext fromModel(ModelContext value) { return value; }
                }, completion);
    }
    private DirectoryWorkflowRunner.CompletionHandler<ModelContext> passthrough() { return (model, execution, scope) -> model; }
    private ModelContext readModel() {
        return new ModelContext(Map.of("status", jdbc.queryForObject("SELECT status FROM workflow_row", Integer.class),
                "orderDetailList", List.of(Map.of("status", jdbc.queryForObject("SELECT detail_status FROM workflow_row", Integer.class))), "payInfo", Map.of()));
    }
    private int rows() { return jdbc.queryForObject("SELECT COUNT(*) FROM workflow_row", Integer.class); }
    private void assertRolledBack() {
        assertEquals(0, rows()); assertEquals(0, manager.commits); assertEquals(1, manager.rollbacks);
        assertEquals(0, afterCommit.get()); assertEquals(0, original.read("status"));
    }
    private static class CountingManager extends DataSourceTransactionManager {
        int commits; int rollbacks; boolean failCommit;
        CountingManager(JdbcDataSource ds) { super(ds); setRollbackOnCommitFailure(true); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            commits++;
            if (failCommit) throw new IllegalStateException("injected failure before physical commit");
            super.doCommit(status);
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; super.doRollback(status); }
    }
}
