package com.dec.lite.spring;

import com.dec.lite.action.*;
import com.dec.lite.directory.*;
import com.dec.lite.information.ModelContext;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.runtime.RuntimeFailure;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** A separate domain using the same Runner, Information, custom Actions and physical transactions. */
class ExpenseApprovalTest {
    enum Mode { EXTERNAL, RUNTIME_MANAGED }
    enum Fault { NONE, ACTION, ACTION_TIMEOUT, COMPLETION_TIMEOUT }
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    DirectoryWorkflowRunner runner;
    CustomActionRegistry custom;
    TestClock clock;
    Instant deadline;
    Fault fault;
    final AtomicInteger committed = new AtomicInteger();
    final AtomicInteger called = new AtomicInteger();
    static final DataOperationAdapter NO_RULES = (rule, invocation) -> { throw new AssertionError(rule.id()); };

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE expense_request(id INT PRIMARY KEY, status INT)");
        jdbc.execute("CREATE TABLE expense_audit(id INT, stage VARCHAR(30), PRIMARY KEY(id,stage))");
        jdbc.update("INSERT INTO expense_request VALUES(1,0)");
        clock = new TestClock(); deadline = clock.instant().plusSeconds(30); fault = Fault.NONE;
        custom = CustomActionRegistry.from(List.of(action("recordSubmitted"), action("recordApproved")));
        runner = new DirectoryWorkflowRunner(new DecYamlParser().parse(
                Path.of(getClass().getClassLoader().getResource("expense-approval").toURI())), custom, clock);
    }

    private CustomAction action(String name) {
        return new CustomAction() {
            public String name() { return name; }
            public void execute(ActionExecutionContext context) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                called.incrementAndGet();
                jdbc.update("INSERT INTO expense_audit VALUES(?,?)", context.model().read("id"), name);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    public void afterCommit() { committed.incrementAndGet(); }
                });
                if (name.equals("recordApproved")) {
                    if (fault == Fault.ACTION) throw new IllegalStateException("audit service failed");
                    if (fault == Fault.ACTION_TIMEOUT) clock.now = deadline;
                }
            }
        };
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void approvalCommitsAllStagesOnlyAfterFinalVerification(Mode mode) {
        var initial = model("800");
        var result = inMode(mode, tx -> advance(tx, initial));
        assertEquals("approved", runner.recognizeDirectory(result, NO_RULES));
        assertEquals(0, initial.read("status"));
        assertEquals(2, jdbc.queryForObject("SELECT status FROM expense_request WHERE id=1", Integer.class));
        assertEquals(2, auditCount()); assertEquals(2, committed.get());
        runner.requireSingleStep("draft", "submitted", false);
        runner.requireSingleStep("submitted", "approved", false);
        assertThrows(DirectoryExecutionException.class, () -> runner.requireSingleStep("draft", "approved", false));
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void eligibilityFailureRollsBackEarlierStage(Mode mode) {
        var error = assertThrows(DirectoryWorkflowException.class, () -> inMode(mode, tx -> advance(tx, model("1200"))));
        assertEquals(RuntimeErrorCode.DEPENDENCY, error.execution().errorDetail().code());
        assertEquals(1, called.get()); assertRolledBack();
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void finalCustomActionFailureRollsBackEarlierActions(Mode mode) {
        fault = Fault.ACTION;
        var error = assertThrows(DirectoryWorkflowException.class, () -> inMode(mode, tx -> advance(tx, model("800"))));
        assertEquals(RuntimeErrorCode.ACTION, error.execution().errorDetail().code());
        assertEquals(2, called.get()); assertRolledBack();
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void expiredRequestNeverInvokesCustomAction(Mode mode) {
        clock.now = deadline;
        var error = assertThrows(DirectoryWorkflowException.class, () -> inMode(mode, tx -> advance(tx, model("800"))));
        assertEquals(RuntimeErrorCode.TIMEOUT, error.execution().errorDetail().code());
        assertEquals(0, called.get()); assertRolledBack();
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void finalCustomActionReturningAtDeadlineCannotCommit(Mode mode) {
        fault = Fault.ACTION_TIMEOUT;
        var original = model("800");
        var error = assertThrows(DirectoryWorkflowException.class, () -> inMode(mode, tx -> advance(tx, original)));
        assertEquals(RuntimeErrorCode.TIMEOUT, error.execution().errorDetail().code());
        assertEquals(0, original.read("status"));
        assertEquals(2, called.get()); assertRolledBack();
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void completionTimeoutAlsoRollsBackFinalWrites(Mode mode) {
        fault = Fault.COMPLETION_TIMEOUT;
        var error = assertThrows(RuntimeFailure.class, () -> inMode(mode, tx -> advance(tx, model("800"))));
        assertEquals(RuntimeErrorCode.TIMEOUT, error.code());
        assertRolledBack();
    }

    private ModelContext advance(WorkflowTransaction tx, ModelContext initial) {
        return runner.advance(initial, "draft", "approved", false, Map.of(), NO_RULES, custom, tx, deadline,
                new DirectoryWorkflowRunner.ModelCodec<>() {
                    public ModelContext toModel(ModelContext model) { return model.copy(); }
                    public ModelContext fromModel(ModelContext model) { return model.copy(); }
                }, (model, execution, scope) -> {
                    assertEquals(0, committed.get());
                    jdbc.update("UPDATE expense_request SET status=? WHERE id=1", model.read("status"));
                    var persisted = new ModelContext(Map.of("id", 1, "status", jdbc.queryForObject(
                            "SELECT status FROM expense_request WHERE id=1", Integer.class),
                            "amount", initial.read("amount"), "reviewerEnabled", true));
                    runner.assertDirectoryMatches(persisted, execution.currentDirectory(), NO_RULES);
                    if (fault == Fault.COMPLETION_TIMEOUT) clock.now = deadline;
                    return persisted;
                });
    }
    private ModelContext inMode(Mode mode, Function<WorkflowTransaction, ModelContext> work) {
        if (mode == Mode.RUNTIME_MANAGED) return work.apply(SpringWorkflowTransactions.runtimeManaged("approvalDb", manager));
        return new TransactionTemplate(manager).execute(status -> work.apply(SpringWorkflowTransactions.externallyManaged(() -> status)));
    }
    private ModelContext model(String amount) { return new ModelContext(Map.of("id", 1, "status", 0, "amount", new BigDecimal(amount), "reviewerEnabled", true)); }
    private int auditCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM expense_audit", Integer.class); }
    private void assertRolledBack() {
        assertEquals(0, auditCount()); assertEquals(0, committed.get());
        assertEquals(0, jdbc.queryForObject("SELECT status FROM expense_request WHERE id=1", Integer.class));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
