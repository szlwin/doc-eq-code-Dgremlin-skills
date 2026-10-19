package com.dec.lite.action;

import com.dec.lite.information.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class RuleProgramTest {
    @Test void malformedNumbersKeywordsAndIndexesFailAsRuleSyntaxErrors() {
        for (String source : List.of("1.2.3 = 1", "9223372036854775808 = 1", "x = not b", "x = and",
                "x = or", "x = if", "exists(not)", "rows[999999999999].id = 1")) {
            assertThrows(ActionExecutionException.class, () -> RuleExpression.compile(source), source);
        }
        assertEquals(true, RuleExpression.compile("item.and = 1 and not (x = false)")
                .evaluate(new RuleExpression.Scope(Map.of("item", Map.of("and", 1), "x", true), Map.of())));
        assertThrows(ActionExecutionException.class, () -> RuleExpression.number(Double.NaN));
    }

    @Test void immutableProgramsSurviveRoutingAndConcurrentBindingsWithoutVariableLeakage() throws Exception {
        var rule = new CompiledRule("R", "compute", "dsl", null, null,
                "#increment : input; every(groups, every(items, value : value + #increment));", "db");
        assertSame(rule.program(), rule.withDataSource("other").program());
        var predicate = new CompiledRule("P", "verify", "checkPattern", null, "input > 0", null, "db");
        assertSame(predicate.expression(), predicate.withDataSource("other").expression());
        var key = new RuleViewKey("order", "OrderInfo", "test");
        var compiled = Map.of(key, new CompiledRuleView(key, "RV", List.of(predicate, rule)));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 1; i <= 30; i++) {
                int input = i;
                futures.add(executor.submit(() -> {
                    var model = new ModelContext(Map.of("input", input, "groups", List.of(
                            Map.of("items", List.of(Map.of("value", 10), Map.of("value", 20))),
                            Map.of("items", List.of(Map.of("value", 30))))));
                    var registry = new RuleViewCompiler().bind(compiled, null);
                    var result = registry.invoke(key, model, Map.of(), new MutationSet());
                    assertEquals(RecognitionResult.Status.TRUE, result.status(), result.errors().toString());
                    assertEquals(input + 10, ((Number) model.read("groups[0].items[0].value")).intValue());
                    assertEquals(input + 20, ((Number) model.read("groups[0].items[1].value")).intValue());
                    assertEquals(input + 30, ((Number) model.read("groups[1].items[0].value")).intValue());
                    assertFalse(model.values().containsKey("#increment"));
                }));
            }
            for (var future : futures) future.get();
        }
    }

    @Test void rowOverlayAndQuantifierItemScopesRemainExplicitAndDoNotModifyModel() {
        var key = new RuleViewKey("order", "OrderInfo", "test");
        var model = new ModelContext(Map.of("value", 10, "limit", 5, "items", List.of(Map.of("value", 2))));
        var rule = new CompiledRule("R", "read", "checkDataPattern", "items", "value = 2 and limit = 5", null, "db");
        var adapter = new RuleDataAdapter(request -> RuleDataAccess.Result.read(List.of(Map.of("value", 2))));
        var registry = new RuleViewCompiler().bind(Map.of(key, new CompiledRuleView(key, "RV", List.of(rule))), adapter);
        assertEquals(RecognitionResult.Status.TRUE, registry.invoke(key, model, Map.of(), new MutationSet()).status());
        assertEquals(10, model.read("value"));
        assertThrows(RuleExpression.MissingValue.class, () -> RuleExpression.compile("every(items, value < limit)")
                .evaluate(new RuleExpression.Scope(model.values(), Map.of())));
    }

    @Test void missingDataPropertyFailsBeforeDriverCallWithActionDiagnostic() {
        var key = new RuleViewKey("order", "OrderInfo", "test");
        var rule = new CompiledRule("RULE-BROKEN", "broken", "query", null, null, null, "db");
        var adapter = new RuleDataAdapter(request -> { fail("Invalid input must not reach the driver"); return null; });
        var registry = new RuleViewCompiler().bind(Map.of(key, new CompiledRuleView(key, "RV", List.of(rule))), adapter);
        var result = registry.invoke(key, new ModelContext(Map.of()), Map.of(), new MutationSet());
        assertEquals(RecognitionResult.Status.ERROR, result.status());
        assertTrue(result.errors().toString().contains("ActionExecutionException"));
        assertTrue(result.errors().toString().contains("RULE-BROKEN requires property"));
    }
}
