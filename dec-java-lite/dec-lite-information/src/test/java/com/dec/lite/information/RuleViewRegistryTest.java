package com.dec.lite.information;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RuleViewRegistryTest {
    @Test void typedFailuresAndCausesSurviveRecognitionCompositionAndTracing() {
        var cause = new IllegalStateException("connection unavailable");
        var failure = new com.dec.lite.runtime.RuntimeFailure(com.dec.lite.runtime.RuntimeErrorCode.INFRASTRUCTURE,
                "RULE-READ", null, "read failed", cause);
        var registry = new RuleViewRegistry().register("order", "OrderInfo", "read", ignored -> { throw failure; });
        var context = new ModelContext(Map.of());
        var result = registry.invoke("order", "read", context, Map.of(), new MutationSet());
        var key = InformationKey.parse("order.available");
        var combined = InformationExpression.not(InformationExpression.reference(key)).evaluate(ignored -> result, context.version())
                .withTrace(new TraceEvent(key, "test", RecognitionResult.Status.ERROR, "test"));
        assertEquals(RecognitionResult.Status.ERROR, combined.status());
        assertSame(failure, combined.failure());
        assertSame(cause, combined.failure().getCause());
    }
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resolvesWithinSystemAndForwardsInvocationState(int overload) {
        ModelContext context = new ModelContext(new LinkedHashMap<>(Map.of("status", 0)));
        MutationSet mutations = new MutationSet();
        Map<String, Object> produced = new LinkedHashMap<>();
        TransactionScope transaction = new TransactionScope() { };
        RuleViewRegistry registry = new RuleViewRegistry()
                .register("payment", "PaymentInfo", "submit", ignored -> fail("Wrong system"))
                .register("order", "OrderInfo", "submit", invocation -> {
                    assertEquals(new RuleViewKey("order", "OrderInfo", "submit"), invocation.key());
                    assertSame(context, invocation.context());
                    assertSame(mutations, invocation.mutations());
                    assertEquals(Map.of("id", 42), invocation.payload());
                    assertNull(invocation.information());
                    if (overload >= 2) assertSame(produced, invocation.produced());
                    if (overload == 3) assertSame(transaction, invocation.transaction());
                    invocation.write("status", 1);
                    invocation.produce("receipt", 42);
                    return RecognitionResult.of(RecognitionResult.Status.TRUE, context.version());
                }).freeze();

        RecognitionResult result = invoke(registry, overload, "order", "submit", context, mutations, produced, transaction);
        assertEquals(RecognitionResult.Status.TRUE, result.status());
        assertEquals(1, context.read("status"));
        assertEquals(1, mutations.mutations().size());
        if (overload >= 2) assertEquals(42, produced.get("receipt"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void missingAndNullReferencesRemainUnresolved(int overload) {
        RuleViewRegistry registry = new RuleViewRegistry()
                .register("payment", "PaymentInfo", "submit", ignored -> fail("Wrong system"));
        ModelContext context = new ModelContext(Map.of());
        for (String[] reference : new String[][] {{"order", "submit"}, {null, "submit"}, {"payment", null}}) {
            RecognitionResult result = invoke(registry, overload, reference[0], reference[1], context,
                    new MutationSet(), new LinkedHashMap<>(), null);
            assertEquals(RecognitionResult.unresolved(context.version(),
                    "no system-scoped RuleView evaluator registered for " + reference[0] + "." + reference[1]), result);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void additionalViewsMakePairAmbiguousButExactKeysStillWork(int overload) {
        AtomicInteger calls = new AtomicInteger();
        RuleViewRegistry.Evaluator evaluator = invocation -> {
            calls.incrementAndGet();
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version());
        };
        RuleViewRegistry registry = new RuleViewRegistry().register("order", "OrderInfo", "submit", evaluator);
        ModelContext context = new ModelContext(Map.of());
        assertEquals(RecognitionResult.Status.TRUE, invoke(registry, overload, "order", "submit", context,
                new MutationSet(), new LinkedHashMap<>(), null).status());
        registry.register("order", "OtherInfo", "submit", evaluator);
        registry.register("order", "ThirdInfo", "submit", evaluator).freeze();

        assertEquals(RecognitionResult.error(context.version(), "ambiguous RuleView action: order.submit"),
                invoke(registry, overload, "order", "submit", context, new MutationSet(), new LinkedHashMap<>(), null));
        assertEquals(1, calls.get());
        assertEquals(RecognitionResult.Status.TRUE, registry.invoke(new RuleViewKey("order", "OtherInfo", "submit"),
                context, Map.of(), new MutationSet()).status());
        assertEquals(2, calls.get());
    }

    @Test
    void rejectedRegistrationsDoNotChangeResolution() {
        RuleViewRegistry.Evaluator evaluator = invocation -> RecognitionResult.of(RecognitionResult.Status.TRUE,
                invocation.context().version());
        RuleViewRegistry registry = new RuleViewRegistry().register("order", "OrderInfo", "submit", evaluator);
        assertThrows(InformationCompilationException.class,
                () -> registry.register("order", "OrderInfo", "submit", evaluator));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("order", "OtherInfo", "submit", null));
        registry.freeze();
        assertThrows(IllegalStateException.class,
                () -> registry.register("order", "OtherInfo", "submit", evaluator));
        assertEquals(RecognitionResult.Status.TRUE, registry.invoke("order", "submit", new ModelContext(Map.of()),
                Map.of(), new MutationSet()).status());
    }

    @Test
    void compositeReferencesDoNotCollideOnSeparators() {
        RuleViewRegistry registry = new RuleViewRegistry()
                .register("a.b", "View", "c", invocation -> RecognitionResult.of(RecognitionResult.Status.TRUE, "first"))
                .register("a", "View", "b.c", invocation -> RecognitionResult.of(RecognitionResult.Status.FALSE, "second"));
        ModelContext context = new ModelContext(Map.of());
        assertEquals(RecognitionResult.Status.TRUE, registry.invoke("a.b", "c", context, Map.of(), new MutationSet()).status());
        assertEquals(RecognitionResult.Status.FALSE, registry.invoke("a", "b.c", context, Map.of(), new MutationSet()).status());
    }

    private RecognitionResult invoke(RuleViewRegistry registry, int overload, String system, String ruleRef,
                                     ModelContext context, MutationSet mutations, Map<String, Object> produced,
                                     TransactionScope transaction) {
        Map<String, Object> payload = Map.of("id", 42);
        return switch (overload) {
            case 1 -> registry.invoke(system, ruleRef, context, payload, mutations);
            case 2 -> registry.invoke(system, ruleRef, context, payload, mutations, produced);
            case 3 -> registry.invoke(system, ruleRef, context, payload, mutations, produced, transaction);
            default -> throw new IllegalArgumentException("Unknown overload: " + overload);
        };
    }
}
