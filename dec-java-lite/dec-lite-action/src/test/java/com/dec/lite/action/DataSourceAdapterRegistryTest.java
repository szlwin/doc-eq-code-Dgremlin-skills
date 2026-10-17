package com.dec.lite.action;

import com.dec.lite.information.*;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DataSourceAdapterRegistryTest {
    private Map<RuleViewKey, CompiledRuleView> compiled() throws Exception {
        return new RuleViewCompiler().compile(new DecYamlParser().parse(
                Path.of(getClass().getClassLoader().getResource("mix").toURI())));
    }
    private static DataSourceAdapter provider(String name, java.util.function.Consumer<CompiledRule> call) {
        return new DataSourceAdapter(name, (rule, invocation) -> {
            call.accept(rule); invocation.write("implementation", name);
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), name);
        });
    }
    private static Map<String, String> choices(String db, String errors) {
        return Map.of("data1", db, "paymentService", db, "mongo1", errors);
    }
    @Test void selectsImplementationsWithoutChangingRuleDataSourcePrecedence() throws Exception {
        var calls = new ArrayList<String>();
        var registry = DataSourceAdapterRegistry.from(List.of(
                provider("db-a", r -> calls.add("A:" + r.dataSource())), provider("db-b", r -> calls.add("B:" + r.dataSource()))));
        var selected = registry.select(choices("db-a", "db-b"));
        var views = new RuleViewCompiler().bind(compiled(), selected);
        for (String rule : List.of("receivePayResult", "recordPyaError")) {
            var result = views.invoke("payment", rule, new ModelContext(Map.of()), Map.of(), new MutationSet());
            assertEquals(RecognitionResult.Status.TRUE, result.status(), result.errors().toString());
        }
        assertEquals(List.of("A:data1", "B:mongo1"), calls);
        assertEquals(choices("db-a", "db-b"), selected.implementations());
    }
    @Test void rejectsDuplicateUnknownAndIncompleteChoicesBeforeAnyRuleRuns() throws Exception {
        var count = new AtomicInteger(); var adapter = provider("local", r -> count.incrementAndGet());
        assertThrows(IllegalArgumentException.class, () -> DataSourceAdapterRegistry.from(List.of(adapter, adapter)));
        var registry = DataSourceAdapterRegistry.from(List.of(adapter)); var compiled = compiled();
        assertThrows(IllegalArgumentException.class, () -> registry.select(Map.of("data1", "missing")));
        assertThrows(IllegalArgumentException.class, () -> new RuleViewCompiler().bind(compiled, registry.select(Map.of("data1", "local"))));
        var unknownLogical = new HashMap<>(choices("local", "local")); unknownLogical.put("typo", "local");
        assertThrows(IllegalArgumentException.class, () -> new RuleViewCompiler().bind(compiled, registry.select(unknownLogical)));
        assertEquals(0, count.get());
    }
    @Test void independentSelectionsRemainIsolatedUnderConcurrentExecution() throws Exception {
        var registry = DataSourceAdapterRegistry.from(List.of(provider("a", r -> {}), provider("b", r -> {})));
        var choices = new HashMap<>(choices("a", "a"));
        var first = registry.select(choices); choices.put("data1", "b");
        assertEquals("a", first.implementations().get("data1"));
        var compiled = compiled();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                String expected = i % 2 == 0 ? "a" : "b";
                futures.add(executor.submit(() -> {
                    var rules = new RuleViewCompiler().bind(compiled, registry.select(choices(expected, expected)));
                    var model = new ModelContext(Map.of());
                    assertEquals(RecognitionResult.Status.TRUE, rules.invoke("payment", "receivePayResult", model, Map.of(), new MutationSet()).status());
                    assertEquals(expected, model.read("implementation"));
                }));
            }
            for (var future : futures) future.get();
        }
    }
}
