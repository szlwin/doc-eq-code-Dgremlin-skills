package com.dec.lite.information;

import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixInformationEngineTest {
    @Test
    void compilesAllMixInformationAndRecomputesAfterMaterialization() throws Exception {
        DecProject project = new DecYamlParser().parse(resource("mix/systems-order.yaml"));
        InformationCompilation compilation = new InformationParser().parse(project);
        assertEquals(16, compilation.definitions().size());
        assertEquals(16, compilation.topologicalOrder().size());

        RuleViewRegistry registry = mixRuleViews();
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, registry));
        ModelContext context = model(1L, 0L, 0L, "SUCCESS");

        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("user", "effective"), context).status());
        assertEquals(RecognitionResult.Status.FALSE, engine.evaluate(key("order", "ordered"), context).status());
        assertEquals(RecognitionResult.Status.FALSE, engine.evaluate(key("order", "payable"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("payment", "success"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("payment", "completed"), context).status());

        engine.materialize(key("order", "ordered"), context);
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("order", "ordered"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("order", "payable"), context).status());
        engine.materialize(key("order", "paySuccessStatus"), context);
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("common", "paySuccess"), context).status());
        engine.materialize(key("order", "payErrorStatus"), context);

        MutationSet resultChange = new MutationSet();
        context.write("payInfo.resultCode", "ERROR", resultChange);
        Set<InformationKey> invalidated = engine.invalidate(resultChange);
        assertTrue(invalidated.contains(key("payment", "success")));
        assertTrue(invalidated.contains(key("payment", "completed")));
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("payment", "error"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("common", "payError"), context).status());
    }

    @Test
    void usesSystemScopedRuleViewDispatchAndProducesTrace() throws Exception {
        InformationCompilation compilation = new InformationParser().parse(new DecYamlParser().parse(resource("mix/systems-order.yaml")));
        RuleViewRegistry registry = new RuleViewRegistry()
                .register("user", "UserInfo", "isActivated", invocation -> RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "user/UserInfo/isActivated"));
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, registry));
        RecognitionResult result = engine.evaluate(key("user", "activated"), model(0L, 0L, 0L, "UNKNOWN"));
        assertEquals(RecognitionResult.Status.TRUE, result.status());
        assertTrue(result.evidence().contains("user/UserInfo/isActivated"));
        assertTrue(result.trace().stream().anyMatch(event -> event.key().equals(key("user", "activated"))));
        assertEquals(RecognitionResult.Status.UNRESOLVED, engine.evaluate(key("user", "certified"), model(0L, 0L, 0L, "UNKNOWN")).status());
    }

    @Test
    void propagatesStatesAndShortCircuitsCompositeEvaluation() throws Exception {
        Path input = java.nio.file.Files.createTempDirectory("dec-information-states-");
        java.nio.file.Files.writeString(input.resolve("systems.yaml"), """
                kind: systems
                version: dec/v1
                systems:
                  - id: SYS-X
                    name: x
                    information:
                      - {id: INFO-X-FALSE, name: "false", viewRef: V, ruleRef: falseRule}
                      - {id: INFO-X-TRUE, name: "true", viewRef: V, ruleRef: trueRule}
                      - {id: INFO-X-ERROR, name: "error", viewRef: V, ruleRef: errorRule}
                      - {id: INFO-X-UNRESOLVED, name: unresolved, viewRef: V, ruleRef: unresolvedRule}
                      - {id: INFO-X-AND, name: andShortCircuit, expression: x.false and x.error}
                      - {id: INFO-X-OR, name: orShortCircuit, expression: x.true or x.error}
                      - {id: INFO-X-MIXED, name: mixed, expression: x.unresolved or x.true}
                """);
        InformationCompilation compilation = new InformationParser().parse(new DecYamlParser().parse(input));
        RuleViewRegistry registry = new RuleViewRegistry()
                .register("x", "V", "falseRule", ignored -> RecognitionResult.of(RecognitionResult.Status.FALSE, "v", "false"))
                .register("x", "V", "trueRule", ignored -> RecognitionResult.of(RecognitionResult.Status.TRUE, "v", "true"))
                .register("x", "V", "errorRule", ignored -> { throw new IllegalStateException("broken"); });
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, registry));
        ModelContext context = model(0L, 0L, 0L, "UNKNOWN");
        assertEquals(RecognitionResult.Status.FALSE, engine.evaluate(key("x", "andShortCircuit"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("x", "orShortCircuit"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(key("x", "mixed"), context).status());
        assertEquals(RecognitionResult.Status.ERROR, engine.evaluate(key("x", "error"), context).status());
        assertEquals(RecognitionResult.Status.UNRESOLVED, engine.evaluate(key("x", "unresolved"), context).status());
    }

    @Test
    void rejectsInvalidConfigurationsAndUnauthorizedMaterialization() throws Exception {
        Path input = java.nio.file.Files.createTempDirectory("dec-information-invalid-");
        java.nio.file.Files.writeString(input.resolve("systems.yaml"), """
                kind: systems
                version: dec/v1
                systems:
                  - id: SYS-X
                    name: x
                    information:
                      - {id: INFO-X-BAD, name: bad, viewRef: V, ruleData: order.ordered}
                """);
        assertThrows(InformationCompilationException.class, () -> new InformationParser().parse(new DecYamlParser().parse(input)));

        java.nio.file.Files.writeString(input.resolve("systems.yaml"), """
                kind: systems
                version: dec/v1
                systems:
                  - id: SYS-X
                    name: x
                    information:
                      - id: INFO-X-COMPOSITE
                        name: composite
                        expression: x.other
                        changeData: "status : 1"
                      - id: INFO-X-OTHER
                        name: other
                        viewRef: V
                        ruleRef: otherRule
                """);
        assertThrows(InformationCompilationException.class, () -> new InformationParser().parse(new DecYamlParser().parse(input)));

        InformationCompilation mix = new InformationParser().parse(new DecYamlParser().parse(resource("mix/systems-order.yaml")));
        InformationEngine guarded = new InformationEngine(new InformationEngineContext(mix, mixRuleViews()));
        ModelContext protectedContext = new ModelContext(modelValues(0L, 0L, 0L, "UNKNOWN"), "v1", path -> { throw new SecurityException("write denied: " + path); });
        assertThrows(SecurityException.class, () -> guarded.materialize(key("order", "ordered"), protectedContext));
        assertThrows(InformationCompilationException.class, () -> guarded.materialize(key("order", "payable"), model(0L, 0L, 0L, "UNKNOWN")));
    }

    private static RuleViewRegistry mixRuleViews() {
        return new RuleViewRegistry()
                .register("user", "UserInfo", "isActivated", i -> bool(i.context().read("active"), 1L, "user.activated"))
                .register("user", "UserInfo", "isCertified", i -> bool(i.context().read("certified"), 1L, "user.certified"))
                .register("order", "OrderInfo", "isWaitPay", i -> bool(i.context().read("status"), 1L, "order.waitPay"))
                .register("payment", "OrderInfo", "hasPaymentInfo", i -> bool(i.context().read("payInfo.id"), null, "payment.paymentInfo"))
                .register("payment", "OrderInfo", "hasPayResult", i -> bool(i.context().read("payInfo.resultCode"), null, "payment.hasResult"))
                .register("payment", "OrderInfo", "isPaySuccess", i -> bool(i.context().read("payInfo.resultCode"), "SUCCESS", "payment.success"))
                .register("payment", "OrderInfo", "isPayError", i -> bool(i.context().read("payInfo.resultCode"), "ERROR", "payment.error"));
    }

    private static RecognitionResult bool(Object actual, Object expected, String evidence) {
        boolean value = expected == null ? actual != null : expected.equals(actual);
        return RecognitionResult.of(value ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, "v", evidence);
    }

    private static ModelContext model(Long active, Long status, Long detailStatus, String resultCode) {
        return new ModelContext(modelValues(active, status, detailStatus, resultCode));
    }

    private static Map<String, Object> modelValues(Long active, Long status, Long detailStatus, String resultCode) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("active", active);
        values.put("certified", 1L);
        values.put("status", status);
        values.put("orderDetailList", List.of(new LinkedHashMap<>(Map.of("status", detailStatus)), new LinkedHashMap<>(Map.of("status", detailStatus))));
        values.put("payInfo", new LinkedHashMap<>(Map.of("id", 1L, "resultCode", resultCode)));
        return values;
    }

    private static InformationKey key(String system, String name) { return new InformationKey(system, name); }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(MixInformationEngineTest.class.getClassLoader().getResource(name).toURI());
    }
}
