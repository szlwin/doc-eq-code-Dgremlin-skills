package com.dec.lite.action;

import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionRuntimeTest {
    @Test
    void executesMixRuleViewActionAndPropagatesMutationToInformation() throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        InformationCompilation compilation = new InformationParser().parse(project);
        RuleViewRegistry views = mixViews();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, views));
        ActionDefinition saveOrder = new BusinessActionParser().parse(project).stream()
                .filter(action -> action.name().equals("saveOrder")).findFirst().orElseThrow();

        ModelContext model = model();
        ActionResult result = new ActionRuntime(views, new CustomActionRegistry()).execute(saveOrder, engine, model);
        assertEquals(ActionStatus.SUCCESS, result.status());
        assertEquals(3, result.mutations().mutations().size());
        assertTrue(result.producedData().containsKey("OrderInfo"));
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(new InformationKey("order", "ordered"), model).status());
        assertTrue(result.trace().stream().anyMatch(trace -> trace.operation().equals("invoke")));
    }

    @Test
    void pipelineRunsOrderedActionsAndVerifiesDirectoryGate() throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        InformationCompilation compilation = new InformationParser().parse(project);
        RuleViewRegistry views = mixViews();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, views));
        List<ActionDefinition> actions = new BusinessActionParser().parse(project);
        assertEquals(8, actions.size());
        ActionDefinition saveOrder = actions.stream().filter(action -> action.name().equals("saveOrder")).findFirst().orElseThrow();
        ActionDefinition startPay = actions.stream().filter(action -> action.name().equals("startPay")).findFirst().orElseThrow();
        ActionExecutionContext context = new ActionExecutionContext(engine, model(), Map.of(), java.time.Instant.MAX);
        List<ActionResult> results = new ActionPipeline(new ActionRuntime(views, new CustomActionRegistry()))
                .execute(List.of(saveOrder, startPay), context);
        assertEquals(2, results.size());
        assertEquals(ActionStatus.SUCCESS, results.get(0).status());
        assertEquals(ActionStatus.SUCCESS, results.get(1).status());
        assertEquals(2L, context.model().read("status"));
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(new InformationKey("payment", "paymentInfo"), context.model()).status());
    }

    @Test
    void customActionRequiresRegistrationAndUsesSameResultContract() throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        ActionDefinition sms = new BusinessActionParser().parse(project).stream()
                .filter(action -> action.name().equals("smsNotify")).findFirst().orElseThrow();
        InformationCompilation compilation = new InformationParser().parse(new DecYamlParser().parse(resourceDirectory()));
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, new RuleViewRegistry()));
        ActionRuntime runtime = new ActionRuntime(new RuleViewRegistry(), new CustomActionRegistry());
        ActionResult missing = runtime.execute(sms, engine, model());
        assertEquals(ActionStatus.FAILED, missing.status());
        assertTrue(missing.error().contains("not registered"));

        CustomActionRegistry registry = new CustomActionRegistry().register(new CustomAction() {
            public String name() { return "smsNotify"; }
            public void execute(ActionExecutionContext context) { context.produce("Notification", Map.of("sent", true)); }
        });
        ActionResult success = new ActionRuntime(new RuleViewRegistry(), registry).execute(sms, engine, model());
        assertEquals(ActionStatus.SUCCESS, success.status());
        assertTrue(success.producedData().containsKey("Notification"));
    }

    @Test
    void failFastRejectsMissingProduceAndUnauthorizedMutation() throws Exception {
        DecProject project = new DecYamlParser().parse(resourceDirectory());
        InformationCompilation compilation = new InformationParser().parse(project);
        RuleViewRegistry views = new RuleViewRegistry().register("order", "OrderInfo", "isWaitPay", ignored -> RecognitionResult.of(RecognitionResult.Status.TRUE, "v", "waitPay"));
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, views));
        ActionDefinition action = new ActionDefinition("ACT-X", "x", "d", "order", "isWaitPay", null,
                List.of(), null, List.of(new ProduceDefinition("P-X", "Missing", null, true, 1, "action", "test")), Map.of(), "fail-fast", Path.of("test.yaml"));
        ActionResult missing = new ActionRuntime(views, new CustomActionRegistry()).execute(action, engine, model());
        assertEquals(ActionStatus.FAILED, missing.status());
        assertTrue(missing.error().contains("Produce"));

        ActionDefinition write = new ActionDefinition("ACT-W", "write", "d", "order", "isWaitPay", null,
                List.of(), null, List.of(), Map.of(), "fail-fast", Path.of("test.yaml"));
        ModelContext guarded = new ModelContext(model().values(), "v1", path -> { throw new SecurityException("denied " + path); });
        ActionResult denied = new ActionRuntime(views, new CustomActionRegistry()).execute(write, engine, guarded);
        assertEquals(ActionStatus.SUCCESS, denied.status()); // evaluator below does not write; guard is tested by a writing evaluator

        RuleViewRegistry writingViews = new RuleViewRegistry().register("order", "OrderInfo", "isWaitPay", invocation -> {
            invocation.write("status", 9L);
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "write");
        });
        ActionResult blocked = new ActionRuntime(writingViews, new CustomActionRegistry()).execute(write, engine, guarded);
        assertEquals(ActionStatus.FAILED, blocked.status());
        assertTrue(blocked.error().contains("denied"));
    }

    @Test
    void parserRejectsLegacyRuleReferenceEvenWhenItIsTheOnlyReference() {
        assertThrows(ActionExecutionException.class, () -> new BusinessActionParser().parse(new DecYamlParser().parse(writeTemp("""
                kind: business
                version: dec/v1
                business:
                  id: BUS-X
                  name: x
                  directories:
                    - name: d
                      informationRef: order.ordered
                      actions:
                        - id: ACT-X
                          name: x
                          systemRef: order
                          ref-rule: legacy
                """))));
    }

    private static RuleViewRegistry mixViews() {
        return new RuleViewRegistry()
                .register("user", "UserInfo", "isActivated", invocation -> bool(invocation.context().read("active"), "user.activated"))
                .register("user", "UserInfo", "isCertified", invocation -> bool(invocation.context().read("certified"), "user.certified"))
                .register("order", "OrderInfo", "save-Order", invocation -> {
                    invocation.write("status", 1L);
                    invocation.write("orderDetailList[0].status", 1L);
                    invocation.write("orderDetailList[1].status", 1L);
                    invocation.produce("OrderInfo", Map.of("status", 1L));
                    return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "order.saveOrder");
                })
                .register("payment", "OrderInfo", "pay", invocation -> {
                    invocation.write("payInfo.id", 100L);
                    invocation.produce("PaymentInfo", Map.of("id", 100L));
                    return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "payment.pay");
                })
                .register("payment", "OrderInfo", "hasPaymentInfo", invocation -> bool(invocation.context().read("payInfo.id"), "payment.paymentInfo"))
                .register("payment", "OrderInfo", "hasPayResult", invocation -> bool(invocation.context().read("payInfo.resultCode"), "payment.hasResult"))
                .register("payment", "OrderInfo", "isPaySuccess", invocation -> boolEquals(invocation.context().read("payInfo.resultCode"), "SUCCESS", "payment.success"))
                .register("payment", "OrderInfo", "isPayError", invocation -> boolEquals(invocation.context().read("payInfo.resultCode"), "ERROR", "payment.error"))
                .register("order", "OrderInfo", "isWaitPay", invocation -> boolEquals(invocation.context().read("status"), 1L, "order.waitPay"));
    }

    private static RecognitionResult bool(Object value, String evidence) {
        return RecognitionResult.of(value != null ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, "v", evidence);
    }
    private static RecognitionResult boolEquals(Object actual, Object expected, String evidence) {
        return RecognitionResult.of(expected.equals(actual) ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, "v", evidence);
    }
    private static ModelContext model() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", 0L);
        values.put("active", 1L);
        values.put("certified", 1L);
        values.put("orderDetailList", List.of(new LinkedHashMap<>(Map.of("status", 0L)), new LinkedHashMap<>(Map.of("status", 0L))));
        values.put("payInfo", new LinkedHashMap<String, Object>());
        return new ModelContext(values);
    }
    private static Path resourceDirectory() throws URISyntaxException {
        return Path.of(ActionRuntimeTest.class.getClassLoader().getResource("mix").toURI());
    }
    private static Path writeTemp(String yaml) {
        try {
            Path path = java.nio.file.Files.createTempFile("business-action-", ".yaml");
            java.nio.file.Files.writeString(path, yaml);
            return path;
        } catch (java.io.IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
