package com.dec.lite.directory;

import com.dec.lite.action.*;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.session.TransactionCoordinator;
import com.dec.lite.session.WorkflowCallback;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.SessionError;
import com.dec.lite.runtime.RuntimeErrorCode;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryEngineTest {
    @Test
    void directoryCallbacksAndProduceConsumerShareScopeAndFailureCallbackPreservesError() throws Exception {
        Fixture success = fixture("SUCCESS");
        List<String> calls = new ArrayList<>();
        success.context().session().onWorkflow(new WorkflowCallback() {
            public void before(ActionDefinition action, ExecutionSession session) { calls.add("before:" + action.id()); }
            public void after(ActionDefinition action, ActionResult result, ExecutionSession session) { calls.add("after:" + action.id()); }
        });
        success.context().session().consumeProduced((action, produced, session) -> {
            assertEquals(TransactionCoordinator.State.ACTIVE, session.transactions().state());
            calls.add("consume:" + action.id());
        });
        assertTrue(success.engine().executeTo("success", success.context()).success());
        assertTrue(calls.get(0).startsWith("before:"));
        assertTrue(calls.stream().anyMatch(value -> value.startsWith("consume:")));
        assertEquals(1, success.transaction().commits);

        Fixture failure = fixture("SUCCESS");
        List<SessionError> errors = new ArrayList<>();
        failure.context().session().consumeProduced((action, produced, session) -> { throw new IllegalStateException("consumer broke"); });
        failure.context().session().onWorkflow(new WorkflowCallback() {
            public void failed(ActionDefinition action, SessionError error, ExecutionSession session) {
                errors.add(error);
                throw new IllegalStateException("failure callback broke");
            }
        });
        DirectoryExecutionResult result = failure.engine().executeTo("success", failure.context());
        assertFalse(result.success());
        assertEquals(RuntimeErrorCode.PRODUCE, result.errorDetail().code());
        assertEquals(1, failure.transaction().rollbacks);
        assertEquals(1, errors.size());
        assertTrue(result.errorDetail().trace().stream().anyMatch(event -> event.phase().equals("TRANSACTION_ROLLED_BACK")));
        assertEquals(2, failure.context().session().errors().size());
        assertEquals(0L, failure.context().model().read("status"));
    }
    @Test
    void successPathRunsStartPayOnceAndSelectsOnlySuccess() throws Exception {
        Fixture fixture = fixture("SUCCESS");
        DirectoryExecutionResult result = fixture.engine().executeTo("success", fixture.context());
        assertTrue(result.success(), result.error());
        assertEquals("success", result.currentDirectory());
        assertEquals(List.of("ordered", "paying", "PayResult", "success"), result.plan().directories());
        assertEquals(List.of("execution", "execution", "case"), result.plan().edgeKinds());
        assertEquals(1, count(fixture.calls(), "insertPay"));
        assertEquals(1, count(fixture.calls(), "updatePayResult"));
        assertEquals(0, count(fixture.calls(), "recordPyaError"));
        assertEquals(3L, fixture.context().model().read("status"));
        assertEquals(1, fixture.transaction().commits);
        assertEquals(0, fixture.transaction().rollbacks);
        assertEquals(1, fixture.transactionScopes().size()); // order and payment use the same session transaction
        assertTrue(result.trace().stream().anyMatch(event -> event.stage() == DirectoryStage.CLASSIFYING && event.detail().equals("success=TRUE")));
    }

    @Test
    void resultTargetAutomaticallyClassifiesAndExecutesSelectedCase() throws Exception {
        Fixture fixture = fixture("SUCCESS");
        DirectoryExecutionResult result = fixture.engine().executeTo("PayResult", fixture.context());
        assertTrue(result.success(), result.error());
        assertEquals("success", result.currentDirectory());
        assertEquals(List.of("ordered", "paying", "PayResult", "success"), result.plan().directories());
        assertEquals(3L, fixture.context().model().read("status"));
    }

    @Test
    void errorPathExecutesErrorActionsInOrderAndBackRestoresPaying() throws Exception {
        Fixture fixture = fixture("ERROR");
        DirectoryExecutionResult result = fixture.engine().executeTo("error", fixture.context());
        assertTrue(result.success(), result.error());
        assertEquals(List.of("recordPyaError", "smsNotify"), fixture.calls().stream()
                .filter(value -> value.equals("recordPyaError") || value.equals("smsNotify")).toList());
        assertEquals(4L, fixture.context().model().read("status"));
        assertEquals("error", fixture.context().currentDirectory());

        DirectoryExecutionResult back = fixture.engine().backTo("paying", fixture.context());
        assertTrue(back.success(), back.error());
        assertEquals(List.of("error", "PayResult", "paying"), back.plan().directories());
        assertEquals("paying", fixture.context().currentDirectory());
        assertNull(fixture.context().model().read("payInfo.resultCode"));
        assertEquals(2L, fixture.context().model().read("status"));
        assertTrue(back.trace().stream().anyMatch(event -> event.detail() != null && event.detail().contains("resetPayResult")));
        assertEquals(2, fixture.transaction().commits);
    }

    @Test
    void dependencyFailureRollsBackWholePathAndDoesNotRunPayAction() throws Exception {
        Fixture fixture = fixture("SUCCESS", 0L);
        DirectoryExecutionResult result = fixture.engine().executeTo("success", fixture.context());
        assertFalse(result.success());
        assertTrue(result.error().contains("Dependency"));
        assertEquals(0L, fixture.context().model().read("status"));
        assertEquals(0, count(fixture.calls(), "insertPay"));
        assertEquals(0, fixture.transaction().commits);
        assertEquals(1, fixture.transaction().rollbacks);
    }

    @Test
    void zeroOrWrongCaseCannotCompleteAndRollsBack() throws Exception {
        Fixture zero = fixture("UNKNOWN");
        DirectoryExecutionResult zeroResult = zero.engine().executeTo("success", zero.context());
        assertFalse(zeroResult.success());
        assertTrue(zeroResult.error().contains("exactly one match"));
        assertEquals(0L, zero.context().model().read("status"));
        assertEquals(1, zero.transaction().rollbacks);

        Fixture wrong = fixture("ERROR");
        DirectoryExecutionResult wrongResult = wrong.engine().executeTo("success", wrong.context());
        assertFalse(wrongResult.success());
        assertTrue(wrongResult.error().contains("differs from classified case"));
        assertEquals(0L, wrong.context().model().read("status"));

        Fixture duplicate = fixture("SUCCESS");
        Path temp = Files.createTempDirectory("duplicate-case-");
        Files.copy(resourceDirectory().resolve("systems-order.yaml"), temp.resolve("systems-order.yaml"));
        for (String rule : List.of("rule-user.yaml", "rule-order.yaml", "rule-payment.yaml")) Files.copy(resourceDirectory().resolve(rule), temp.resolve(rule));
        String duplicated = Files.readString(resourceDirectory().resolve("business-order-payment.yaml"))
                .replace("- rel: error\n      role: case\n      informationRef: payment.error",
                        "- rel: error\n      role: case\n      informationRef: payment.success");
        Files.writeString(temp.resolve("business.yaml"), duplicated);
        DirectoryGraph graph = new DirectoryGraphCompiler().compile(new DecYamlParser().parse(temp), duplicate.context().information().compilation());
        DirectoryExecutionResult multi = new DirectoryEngine(graph).executeTo("success", duplicate.context());
        assertFalse(multi.success());
        assertTrue(multi.error().contains("exactly one match"));
    }

    @Test
    void graphRejectsCycleMultiParentAndPayingAsCase() throws Exception {
        String source = Files.readString(resourceDirectory().resolve("business-order-payment.yaml"));
        assertGraphFails(source.replace("    isRoot: true\n", "    isRoot: true\n    subDirectories:\n    - rel: PayResult\n"), "cycle");
        assertGraphFails(source.replace("    isRoot: true\n", "    isRoot: true\n    subDirectories:\n    - rel: paying\n"), "multiple parents");
        assertGraphFails(source.replace("- rel: success\n      role: case", "- rel: paying\n      role: case"), "case target cannot define dependencies");
    }
    @Test void rejectsLegacyDirectoryAttributes() throws Exception {
        String source = Files.readString(resourceDirectory().resolve("business-order-payment.yaml"));
        assertGraphFails(source.replace("role: case", "role: predecessor"), "predecessor");
        assertGraphFails(source.replace("role: case", "role: case\n      anyOne: true"), "any-one/mutual-exclusion");
        assertGraphFails(source.replace("    isRoot: true", "    viewRef: OrderInfo\n    isRoot: true"), "viewRef");
    }

    @Test
    void graphAndPathRejectInvalidCaseAndDirectCaseSkip() throws Exception {
        Fixture fixture = fixture("SUCCESS");
        assertEquals(2, fixture.engine().graph().executionEdges().size());
        assertEquals(2, fixture.engine().graph().caseEdges().size());
        assertEquals(1, fixture.engine().graph().backEdges().size());
        assertThrows(DirectoryExecutionException.class, () -> new PathPlanner().plan(fixture.engine().graph(), "ordered", "missing"));
        assertThrows(DirectoryExecutionException.class, () -> new BackPlanner().plan(fixture.engine().graph(), "success", "ordered"));
        List<CaseEdge> ambiguousCases = new ArrayList<>(fixture.engine().graph().caseEdges());
        ambiguousCases.add(new CaseEdge("paying", "success", new InformationKey("payment", "success")));
        DirectoryGraph ambiguous = new DirectoryGraph(fixture.engine().graph().directories(),
                fixture.engine().graph().executionEdges(), ambiguousCases, fixture.engine().graph().backEdges(), "ordered");
        assertThrows(DirectoryExecutionException.class, () -> new PathPlanner().plan(ambiguous, null, "success"));

        DecProject complete = new DecYamlParser().parse(resourceDirectory());
        InformationCompilation info = new InformationParser().parse(complete);
        assertThrows(IllegalStateException.class, () -> new DirectoryGraphCompiler().compileReady(complete, info, new CustomActionRegistry()));

        Path temp = Files.createTempDirectory("invalid-directory-");
        Files.copy(resourceDirectory().resolve("systems-order.yaml"), temp.resolve("systems-order.yaml"));
        for (String rule : List.of("rule-user.yaml", "rule-order.yaml", "rule-payment.yaml")) Files.copy(resourceDirectory().resolve(rule), temp.resolve(rule));
        String invalid = Files.readString(resourceDirectory().resolve("business-order-payment.yaml"))
                .replace("role: case\n      informationRef: payment.success", "role: predecessor\n      informationRef: payment.success");
        Files.writeString(temp.resolve("business.yaml"), invalid);
        DecProject project = new DecYamlParser().parse(temp);
        InformationCompilation compilation = new InformationParser().parse(project);
        assertThrows(DirectoryCompilationException.class, () -> new DirectoryGraphCompiler().compile(project, compilation));
    }

    private static Fixture fixture(String resultCode) throws Exception { return fixture(resultCode, 1L); }
    private static Fixture fixture(String resultCode, Long active) throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        InformationCompilation compilation = new InformationParser().parse(project);
        List<String> calls = new ArrayList<>();
        java.util.Set<TransactionScope> transactionScopes = new java.util.HashSet<>();
        CountingTransaction transaction = new CountingTransaction();
        DataOperationAdapter adapter = (rule, invocation) -> {
            calls.add(rule.name());
            transactionScopes.add(invocation.transaction());
            if (!(invocation.transaction() instanceof TransactionCoordinator coordinator) || coordinator.legacyTransaction() != transaction)
                throw new IllegalStateException("wrong transaction scope");
            switch (rule.name()) {
                case "insertOrder" -> { invocation.write("status", 1L); invocation.produce("OrderInfo", Map.of("id", 10L)); }
                case "insertOrderDetail" -> { invocation.write("orderDetailList[0].status", 1L); invocation.write("orderDetailList[1].status", 1L); }
                case "insertPay" -> { invocation.write("payInfo.id", 50L); invocation.produce("PaymentInfo", Map.of("id", 50L)); }
                case "updatePayResult" -> { invocation.write("payInfo.resultCode", invocation.payload().get("resultCode")); invocation.produce("PayResult", Map.of("resultCode", invocation.payload().get("resultCode"))); }
                case "recordPyaError" -> invocation.produce("PyaError", Map.of("code", "P1"));
                default -> throw new IllegalStateException("unexpected external Rule " + rule.name());
            }
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.name());
        };
        RuleViewRegistry registry = new RuleViewCompiler().register(project, adapter);
        InformationEngine information = new InformationEngine(new InformationEngineContext(compilation, registry));
        CustomActionRegistry custom = new CustomActionRegistry().register(new CustomAction() {
            public String name() { return "smsNotify"; }
            public void execute(ActionExecutionContext context) { calls.add("smsNotify"); }
        });
        ActionRuntime actions = new ActionRuntime(registry, custom);
        DirectoryGraph graph = new DirectoryGraphCompiler().compileReady(project, compilation, custom);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("active", active); model.put("certified", 1L); model.put("status", 0L);
        model.put("orderDetailList", List.of(new LinkedHashMap<>(Map.of("status", 0L)), new LinkedHashMap<>(Map.of("status", 0L))));
        model.put("payInfo", new LinkedHashMap<String, Object>());
        DirectoryExecutionContext context = new DirectoryExecutionContext(information, actions, new ModelContext(model),
                Map.of("resultCode", resultCode), transaction, Instant.MAX, null);
        return new Fixture(new DirectoryEngine(graph), context, calls, transaction, transactionScopes);
    }
    private static long count(List<String> calls, String name) { return calls.stream().filter(name::equals).count(); }
    private static void assertGraphFails(String businessYaml, String message) throws Exception {
        Path input = Files.createTempDirectory("invalid-graph-");
        Files.copy(resourceDirectory().resolve("systems-order.yaml"), input.resolve("systems-order.yaml"));
        for (String rule : List.of("rule-user.yaml", "rule-order.yaml", "rule-payment.yaml")) Files.copy(resourceDirectory().resolve(rule), input.resolve(rule));
        Files.writeString(input.resolve("business.yaml"), businessYaml);
        DecProject project = new DecYamlParser().parse(input);
        InformationCompilation information = new InformationParser().parse(project);
        DirectoryCompilationException failure = assertThrows(DirectoryCompilationException.class,
                () -> new DirectoryGraphCompiler().compile(project, information));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }
    private static Path resourceDirectory() throws URISyntaxException { return Path.of(DirectoryEngineTest.class.getClassLoader().getResource("mix").toURI()); }
    private record Fixture(DirectoryEngine engine, DirectoryExecutionContext context, List<String> calls,
                           CountingTransaction transaction, java.util.Set<TransactionScope> transactionScopes) { }
    private static final class CountingTransaction implements ActionTransaction {
        int commits; int rollbacks;
        public void commit() { commits++; }
        public void rollback() { rollbacks++; }
    }
}
