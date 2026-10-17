package com.dec.lite.action;

import com.dec.lite.information.*;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.runtime.RuntimeErrorCode;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ActionListenerTest {
    private ActionDefinition action(List<InformationKey> dependencies, List<ProduceDefinition> produces) {
        return new ActionDefinition("ACT-LISTEN", "work", "directory", "order", null, "work",
                dependencies, null, null, produces, Map.of(), "fail-fast", Path.of("test.yaml"), "BUS-TEST");
    }
    private InformationEngine engine(RuleViewRegistry views) throws Exception {
        var project = new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("mix").toURI()));
        return new InformationEngine(new InformationEngineContext(new InformationParser().parse(project), views));
    }
    private ModelContext model() {
        return new ModelContext(Map.of("status", 0L, "orderDetailList", List.of(Map.of("status", 1L))));
    }
    private ActionListener listener(String id, Consumer<ActionListenerContext> before, Consumer<ActionListenerContext> after) {
        return new ActionListener() {
            public String businessId() { return id; }
            public void before(ActionListenerContext c) { before.accept(c); }
            public void after(ActionListenerContext c, ActionResult r) { assertEquals(ActionStatus.SUCCESS, r.status()); after.accept(c); }
        };
    }
    private CustomActionRegistry custom(Consumer<ActionExecutionContext> work) {
        return CustomActionRegistry.from(List.of(new CustomAction() {
            public String name() { return "work"; }
            public void execute(ActionExecutionContext c) { work.accept(c); }
        }));
    }
    private static final class Tx implements ActionTransaction {
        int commits, rollbacks;
        public void commit() { commits++; }
        public void rollback() { rollbacks++; }
    }

    private static final class OrderView {
        long status;
        OrderView(long status) { this.status = status; }
    }

    @Test void typedViewReflectsCurrentActionAndIsDetachedFromWorkingModel() throws Exception {
        var views = new RuleViewRegistry(); var original = model();
        var binding = new ActionViewBinding("OrderInfo", OrderView.class,
                m -> new OrderView(((Number) m.read("status")).longValue()));
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> {
            assertEquals("OrderInfo", c.viewRef());
            OrderView before = c.view(OrderView.class); assertEquals(0L, before.status);
            before.status = 99; assertEquals(0L, c.read("status"));
            c.write("status", 1L); assertEquals(1L, c.view(OrderView.class).status);
        }, c -> {
            assertEquals(2L, c.view(OrderView.class).status);
            assertThrows(IllegalArgumentException.class, () -> c.view(String.class));
        })));
        var result = new ActionRuntime(views, custom(c -> {
            assertEquals(1L, c.model().read("status")); c.model().write("status", 2L, null);
        }), listeners, Clock.systemUTC(), binding).execute(action(List.of(), List.of()), engine(views), original);
        assertEquals(ActionStatus.SUCCESS, result.status(), result.error()); assertEquals(2L, original.read("status"));
    }

    @Test void missingOrInvalidViewBindingFailsClearly() throws Exception {
        var views = new RuleViewRegistry();
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> c.view(OrderView.class), c -> {})));
        var missing = new ActionRuntime(views, custom(c -> fail("missing binding")), listeners)
                .execute(action(List.of(), List.of()), engine(views), model());
        assertEquals(ActionStatus.FAILED, missing.status()); assertTrue(missing.error().contains("No typed flow View"));
        var invalid = new ActionRuntime(views, custom(c -> fail("invalid binding")), listeners, Clock.systemUTC(),
                new ActionViewBinding("OrderInfo", OrderView.class, m -> "wrong type"))
                .execute(action(List.of(), List.of()), engine(views), model());
        assertEquals(ActionStatus.FAILED, invalid.status()); assertTrue(invalid.error().contains("decoder returned an invalid"));
    }

    @Test void routesActionsByBusinessAndUsesCurrentWorkingModelBeforeCommit() throws Exception {
        var events = new ArrayList<String>();
        var listeners = ActionListenerRegistry.from(List.of(
                listener("BUS-OTHER", c -> fail("unrelated flow"), c -> fail("unrelated flow")),
                listener("BUS-TEST", c -> {
                    assertEquals("BUS-TEST", c.businessId()); events.add("before:" + c.action().id()); c.write("status", 1L);
                }, c -> { events.add("after:" + c.action().id()); assertEquals(2L, c.read("status")); c.write("status", 3L); })));
        var views = new RuleViewRegistry(); var original = model(); var tx = new Tx();
        var runtime = new ActionRuntime(views, custom(c -> {
            events.add("action"); assertEquals(1L, c.model().read("status"));
            c.model().write("status", 2L, null);
        }), listeners);
        var first = action(List.of(), List.of());
        var second = new ActionDefinition("ACT-SECOND", "work", "directory", "order", null, "work",
                List.of(), null, null, List.of(), Map.of(), "fail-fast", Path.of("test.yaml"), "BUS-TEST");
        for (var definition : List.of(first, second)) {
            var result = runtime.execute(definition, new ActionExecutionContext(engine(views), original, Map.of(), Instant.MAX, tx));
            assertEquals(ActionStatus.SUCCESS, result.status(), result.error());
            assertEquals(3L, original.read("status")); assertTrue(result.mutations().touches("status"));
            assertEquals(List.of("listener-before", "invoke", "listener-after"), result.trace().stream().map(ActionTraceEvent::operation).toList());
        }
        assertEquals(List.of("before:ACT-LISTEN", "action", "after:ACT-LISTEN", "before:ACT-SECOND", "action", "after:ACT-SECOND"), events);
        assertEquals(2, tx.commits); assertEquals(0, tx.rollbacks);
    }

    @Test void beforeAndAfterFailuresRollbackWithoutPublishingModel() throws Exception {
        for (boolean failBefore : List.of(true, false)) {
            var calls = new ArrayList<String>(); var tx = new Tx(); var original = model(); var views = new RuleViewRegistry();
            var listener = listener("BUS-TEST", c -> {
                c.write("status", 1L); if (failBefore) throw new IllegalStateException("before blocked");
            }, c -> { throw new IllegalStateException("after blocked"); });
            var result = new ActionRuntime(views, custom(c -> calls.add("action")), ActionListenerRegistry.from(List.of(listener)))
                    .execute(action(List.of(), List.of()), new ActionExecutionContext(engine(views), original, Map.of(), Instant.MAX, tx));
            assertEquals(ActionStatus.FAILED, result.status());
            assertTrue(result.error().contains(failBefore ? "before listener" : "after listener"));
            assertEquals(failBefore ? 0 : 1, calls.size()); assertEquals(0L, original.read("status"));
            assertEquals(0, tx.commits); assertEquals(1, tx.rollbacks);
        }
    }

    @Test void skippedFailedAndMissingProduceActionsDoNotCallSuccessListener() throws Exception {
        var calls = new ArrayList<String>(); var views = new RuleViewRegistry();
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST",
                c -> calls.add("before"), c -> fail("after must only run on success"))));
        var skipped = new ActionRuntime(views, custom(c -> fail("must skip")), listeners)
                .execute(action(List.of(new InformationKey("order", "ordered")), List.of()), engine(views), model());
        assertEquals(ActionStatus.SKIPPED, skipped.status()); assertTrue(calls.isEmpty());
        var failed = new ActionRuntime(views, custom(c -> { throw new IllegalStateException("action failed"); }), listeners)
                .execute(action(List.of(), List.of()), engine(views), model());
        assertEquals(ActionStatus.FAILED, failed.status());
        var missing = new ActionRuntime(views, custom(c -> {}), listeners)
                .execute(action(List.of(), List.of(new ProduceDefinition("P", "missing", null, true, 1, "action", "test"))), engine(views), model());
        assertEquals(RuntimeErrorCode.PRODUCE, missing.failureCode());
    }

    @Test void ruleViewActionsUseSameListenerContract() throws Exception {
        var views = new RuleViewRegistry().register("order", "OrderInfo", "rule", invocation -> {
            assertEquals(1L, invocation.context().read("status")); invocation.write("status", 2L);
            return RecognitionResult.of(RecognitionResult.Status.TRUE, "v", "rule");
        });
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST",
                c -> c.write("status", 1L), c -> { assertEquals(2L, c.read("status")); c.write("status", 3L); })));
        var rule = new ActionDefinition("ACT-LISTEN", "work", "directory", "order", "rule", null,
                List.of(), null, null, List.of(), Map.of(), "fail-fast", Path.of("test.yaml"), "BUS-TEST");
        var original = model();
        var result = new ActionRuntime(views, CustomActionRegistry.from(List.of()), listeners).execute(rule, engine(views), original);
        assertEquals(ActionStatus.SUCCESS, result.status(), result.error()); assertEquals(3L, original.read("status"));
    }

    @Test void afterWritesCannotInvalidateProducePostcondition() throws Exception {
        var views = new RuleViewRegistry(); var original = model();
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> {}, c -> c.write("status", 0L))));
        var produces = List.of(new ProduceDefinition("P", "OrderInfo", new InformationKey("order", "ordered"), true, 1, "action", "test"));
        var result = new ActionRuntime(views, custom(c -> {
            c.model().write("status", 1L, null); c.produce("OrderInfo", Map.of());
        }), listeners).execute(action(List.of(), produces), engine(views), original);
        assertEquals(RuntimeErrorCode.PRODUCE, result.failureCode()); assertEquals(0L, original.read("status"));
    }

    @Test void listenerWritesRespectAccessPolicyAndDeadline() throws Exception {
        var views = new RuleViewRegistry(); var original = model();
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> c.write("status", 1L), c -> {})));
        var guarded = original.copyWithGuard(path -> { throw new SecurityException("write denied"); });
        var denied = new ActionRuntime(views, custom(c -> fail("guard must stop action")), listeners)
                .execute(action(List.of(), List.of()), engine(views), guarded);
        assertEquals(RuntimeErrorCode.ACCESS_DENIED, denied.failureCode());
        var now = new java.util.concurrent.atomic.AtomicReference<>(Instant.EPOCH);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var timeoutListeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> now.set(Instant.EPOCH.plusSeconds(10)), c -> fail("timeout"))));
        var timedOut = new ActionRuntime(views, custom(c -> fail("timeout before action")), timeoutListeners, clock)
                .execute(action(List.of(), List.of()), new ActionExecutionContext(engine(views), model(), Map.of(), Instant.EPOCH.plusSeconds(5)));
        assertEquals(RuntimeErrorCode.TIMEOUT, timedOut.failureCode());
        now.set(Instant.EPOCH);
        var tx = new Tx();
        var afterTimeout = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> {},
                c -> { c.write("status", 9L); now.set(Instant.EPOCH.plusSeconds(10)); })));
        var afterResult = new ActionRuntime(views, custom(c -> {}), afterTimeout, clock)
                .execute(action(List.of(), List.of()), new ActionExecutionContext(engine(views), original, Map.of(), Instant.EPOCH.plusSeconds(5), tx));
        assertEquals(RuntimeErrorCode.TIMEOUT, afterResult.failureCode());
        assertEquals(0L, original.read("status")); assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }

    @Test void registryRejectsMissingAndUnknownIds() {
        assertThrows(IllegalArgumentException.class, () -> ActionListenerRegistry.from(List.of(listener("", c -> {}, c -> {}))));
        var registry = ActionListenerRegistry.from(List.of(listener("BUS-TEST", c -> {}, c -> {})));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(List.of()));
        registry.validate(List.of("BUS-TEST"));
        assertThrows(IllegalArgumentException.class, () -> ActionListenerRegistry.from(List.of(
                listener("BUS-TEST", c -> {}, c -> {}), listener("BUS-TEST", c -> {}, c -> {}))));
    }

    @Test void differentBusinessesDoNotReceiveEachOthersActions() throws Exception {
        var calls = new ArrayList<String>(); var views = new RuleViewRegistry();
        var registry = ActionListenerRegistry.from(List.of(
                listener("BUS-A", c -> calls.add("A:" + c.action().id()), c -> {}),
                listener("BUS-B", c -> calls.add("B:" + c.action().id()), c -> {})));
        var runtime = new ActionRuntime(views, custom(c -> {}), registry);
        for (String business : List.of("BUS-A", "BUS-B", "BUS-NO-LISTENER")) {
            var definition = new ActionDefinition("ACT-SAME-NAME", "work", "directory", "order", null, "work",
                    List.of(), null, null, List.of(), Map.of(), "fail-fast", Path.of("test.yaml"), business);
            assertEquals(ActionStatus.SUCCESS, runtime.execute(definition, engine(views), model()).status());
        }
        assertEquals(List.of("A:ACT-SAME-NAME", "B:ACT-SAME-NAME"), calls);
    }

    @Test void parserPreservesBusinessOwnershipIncludingBackActions() throws Exception {
        var project = new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("mix").toURI()));
        var groups = new BusinessActionParser().parseByBusiness(project);
        assertEquals(Set.of("BUS-ORDER-PAYMENT"), groups.keySet());
        assertTrue(groups.get("BUS-ORDER-PAYMENT").stream().anyMatch(a -> a.ownerDirectory().contains(".back.")));
        assertTrue(groups.get("BUS-ORDER-PAYMENT").stream().allMatch(a -> "BUS-ORDER-PAYMENT".equals(a.businessId())));
    }

    @Test void sharedRuntimeKeepsConcurrentListenerContextsIsolated() throws Exception {
        var views = new RuleViewRegistry(); var compilation = engine(views).context();
        var listeners = ActionListenerRegistry.from(List.of(listener("BUS-TEST",
                c -> c.write("status", c.payload().get("value")), c -> assertEquals(c.payload().get("value"), c.read("status")))));
        var runtime = new ActionRuntime(views, custom(c -> {}), listeners);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (long i = 0; i < 20; i++) {
                long value = i;
                futures.add(executor.submit(() -> {
                    var model = model();
                    var result = runtime.execute(action(List.of(), List.of()), new ActionExecutionContext(
                            new InformationEngine(compilation), model, Map.of("value", value), Instant.MAX));
                    assertEquals(ActionStatus.SUCCESS, result.status()); assertEquals(value, model.read("status"));
                }));
            }
            for (var future : futures) future.get();
        }
    }
}
