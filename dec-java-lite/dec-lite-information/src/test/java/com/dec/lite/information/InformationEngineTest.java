package com.dec.lite.information;

import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InformationEngineTest {
    @Test
    void compilesDagEvaluatesThreeStatesAndMaterializesAtomicInformation() throws Exception {
        Path input = Files.createTempDirectory("dec-information-");
        Files.writeString(input.resolve("systems.yaml"), """
                kind: systems
                version: dec/v1
                systems:
                  - id: SYS-ORDER
                    name: order
                    information:
                      - id: INFO-ORDER-ORDERED
                        name: ordered
                        viewRef: OrderInfo
                        ruleData: |
                          status = 1 and every(orderDetailList, status = 1)
                        changeData: |
                          status : 1;
                          every(orderDetailList, status : 1);
                      - id: INFO-ORDER-PAYABLE
                        name: payable
                        expression: order.ordered or payment.success
                  - id: SYS-PAYMENT
                    name: payment
                    information:
                      - id: INFO-PAYMENT-SUCCESS
                        name: success
                        viewRef: OrderInfo
                        ruleRef: isPaySuccess
                """);
        DecProject project = new DecYamlParser().parse(input);
        InformationCompilation compilation = new InformationParser().parse(project);
        assertEquals(List.of(new InformationKey("order", "ordered"), new InformationKey("payment", "success"), new InformationKey("order", "payable")), compilation.topologicalOrder());
        assertTrue(compilation.graphDigest().matches("[0-9a-f]{64}"));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("status", 0L);
        model.put("orderDetailList", List.of(new LinkedHashMap<>(Map.of("status", 0L)), new LinkedHashMap<>(Map.of("status", 0L))));
        ModelContext context = new ModelContext(model);
        RuleViewRegistry registry = new RuleViewRegistry().register("payment", "OrderInfo", "isPaySuccess",
                ignored -> RecognitionResult.of(RecognitionResult.Status.TRUE, "rule", "payment.success"));
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, registry));
        assertEquals(RecognitionResult.Status.FALSE, engine.evaluate(new InformationKey("order", "ordered"), context).status());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(new InformationKey("order", "payable"), context).status());

        MutationSet mutations = engine.materialize(new InformationKey("order", "ordered"), context);
        assertEquals(3, mutations.mutations().size());
        assertEquals(RecognitionResult.Status.TRUE, engine.evaluate(new InformationKey("order", "ordered"), context).status());
        assertEquals(1L, context.read("status"));
    }

    @Test
    void keepsInformationAndModelExpressionLanguagesSeparateAndRejectsCycles() throws Exception {
        assertThrows(InformationCompilationException.class, () -> new InformationExpressionCompiler().compile("status = 1"));
        assertThrows(InformationCompilationException.class, () -> new ModelExpressionCompiler().compile("order.ordered"));
        Path input = Files.createTempDirectory("dec-information-cycle-");
        Files.writeString(input.resolve("systems.yaml"), """
                kind: systems
                version: dec/v1
                systems:
                  - id: SYS-X
                    name: x
                    information:
                      - {id: INFO-X-A, name: a, expression: x.b}
                      - {id: INFO-X-B, name: b, expression: x.a}
                """);
        assertThrows(InformationCompilationException.class, () -> new InformationParser().parse(new DecYamlParser().parse(input)));
    }
}
