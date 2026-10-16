package com.dec.lite.action;

import com.dec.lite.information.*;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class RuleViewBindingTest {
    @Test void compiledDefinitionsAreImmutableAndBindingMatchesLegacyRegistration() throws Exception {
        var project = new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("mix").toURI()));
        var compiler = new RuleViewCompiler();
        var compiled = compiler.compile(project);
        assertThrows(UnsupportedOperationException.class, compiled::clear);
        assertThrows(UnsupportedOperationException.class, () -> compiled.values().iterator().next().rules().clear());
        var legacy = compiler.register(project, null);
        var bound = compiler.bind(compiled, null);
        for (long status : List.of(1L, 2L, 99L)) {
            var model = new ModelContext(Map.of("status", status));
            assertEquals(legacy.invoke("order", "isWaitPay", model, Map.of(), new MutationSet()).status(),
                    bound.invoke("order", "isWaitPay", model, Map.of(), new MutationSet()).status());
        }
    }

    @Test void concurrentBindingsIsolateAdaptersModelsPayloadsAndMutations() throws Exception {
        var key = new RuleViewKey("order", "OrderInfo", "load");
        var compiled = Map.of(key, new CompiledRuleView(key, "RV-LOAD", List.of(
                new CompiledRule("RULE-LOAD", "load", "get", null, null, null, "test"))));
        var compiler = new RuleViewCompiler();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int request = 0; request < 100; request++) {
                final int owner = request;
                futures.add(executor.submit(() -> {
                    var registry = compiler.bind(compiled, (rule, invocation) -> {
                        assertEquals(owner, invocation.payload().get("owner"));
                        assertEquals(owner, invocation.context().read("owner"));
                        invocation.write("seen", owner);
                        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "loaded");
                    }).freeze();
                    var model = new ModelContext(Map.of("owner", owner));
                    var mutations = new MutationSet();
                    assertEquals(RecognitionResult.Status.TRUE,
                            registry.invoke(key, model, Map.of("owner", owner), mutations).status());
                    assertEquals(owner, model.read("seen"));
                    assertEquals(1, mutations.mutations().size());
                }));
            }
            for (var future : futures) future.get();
        }
    }
}
