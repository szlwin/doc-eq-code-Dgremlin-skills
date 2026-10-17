package com.dec.lite.action;

import com.dec.lite.information.*;
import com.dec.lite.model.*;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActionDataSourceTest {
    private ActionDefinition action(String override, String fallback, boolean custom) {
        return new ActionDefinition("ACT-ROUTE", "route", "root", "order", custom ? null : "route",
                custom ? "route" : null, List.of(), null, null, List.of(), Map.of(), "fail-fast",
                Path.of("design.yaml"), "BUS-ROUTE", override, fallback);
    }
    private Map<RuleViewKey, CompiledRuleView> rules(String source) {
        var key = new RuleViewKey("order", "OrderInfo", "route");
        return Map.of(key, new CompiledRuleView(key, "RV-ROUTE", List.of(
                new CompiledRule("RULE-ROUTE", "route", "update", "status", null, null, source))));
    }
    private DataSourceSelection selection(List<String> calls) {
        var adapter = new DataSourceAdapter("local", (rule, invocation) -> {
            calls.add(rule.dataSource()); invocation.write("status", 1L);
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "routed");
        });
        return DataSourceAdapterRegistry.from(List.of(adapter)).select(Map.of("flow", "local", "rule", "local", "action", "local"));
    }
    private InformationEngine engine(RuleViewRegistry registry) throws Exception {
        var project = new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("mix").toURI()));
        return new InformationEngine(new InformationEngineContext(new InformationParser().parse(project), registry));
    }
    @Test void actionOverridesRuleAndDefaultWithoutMutatingCompilationOrNextInvocation() throws Exception {
        var calls = new ArrayList<String>(); var compiled = rules("rule"); var choice = selection(calls);
        var override = action("action", "flow", false);
        var registry = new RuleViewCompiler().bind(compiled, choice, List.of(override), "flow");
        var runtime = new ActionRuntime(registry, new CustomActionRegistry());
        for (var action : List.of(override, action(null, "flow", false))) {
            var result = runtime.execute(action, engine(registry), new ModelContext(Map.of("status", 0L)));
            assertEquals(ActionStatus.SUCCESS, result.status(), result.error());
        }
        assertEquals(List.of("action", "rule"), calls);
        assertEquals("rule", compiled.values().iterator().next().rules().get(0).dataSource());
    }
    @Test void missingRuleSourceInheritsBusinessDefaultForActionsAndRecognition() throws Exception {
        var calls = new ArrayList<String>();
        var provider = new DataSourceAdapter("local", (rule, invocation) -> {
            calls.add(rule.dataSource());
            return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "ok");
        });
        var selected = DataSourceAdapterRegistry.from(List.of(provider)).select(Map.of("flow", "local"));
        var registry = new RuleViewCompiler().bind(rules(null), selected, List.of(action(null, "flow", false)), "flow");
        var result = new ActionRuntime(registry, new CustomActionRegistry()).execute(action(null, "flow", false), engine(registry), new ModelContext(Map.of()));
        assertEquals(ActionStatus.SUCCESS, result.status(), result.error());
        assertEquals(RecognitionResult.Status.TRUE, registry.invoke("order", "route", new ModelContext(Map.of()), Map.of(), new MutationSet()).status());
        assertEquals(List.of("flow", "flow"), calls);
    }
    @Test void missingOverrideRegistrationFailsAtBinding() {
        var provider = new DataSourceAdapter("local", (rule, invocation) -> { throw new AssertionError("must not execute"); });
        var selected = DataSourceAdapterRegistry.from(List.of(provider)).select(Map.of("flow", "local", "rule", "local"));
        assertThrows(IllegalArgumentException.class, () -> new RuleViewCompiler().bind(rules("rule"), selected,
                List.of(action("action", "flow", false)), "flow"));
    }
    @Test void overriddenRuleDoesNotRequireUnusedRouteButRecognitionStillDoes() {
        var provider = new DataSourceAdapter("local", (r, i) -> RecognitionResult.of(RecognitionResult.Status.TRUE, i.context().version(), "ok"));
        var selected = DataSourceAdapterRegistry.from(List.of(provider)).select(Map.of("flow", "local", "action", "local"));
        var compiled = rules("unselected"); var actions = List.of(action("action", "flow", false));
        assertDoesNotThrow(() -> new RuleViewCompiler().bind(compiled, selected, actions, "flow", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new RuleViewCompiler().bind(compiled, selected, actions, "flow", compiled.keySet()));
    }
    @Test void concurrentActionsCannotLeakOverridesOrTrustPayloadRouting() throws Exception {
        var provider = new DataSourceAdapter("local", (r, i) -> {
            i.write("actualSource", r.dataSource());
            return RecognitionResult.of(RecognitionResult.Status.TRUE, i.context().version(), "ok");
        });
        var selected = DataSourceAdapterRegistry.from(List.of(provider)).select(Map.of("flow", "local", "rule", "local", "action", "local"));
        var registry = new RuleViewCompiler().bind(rules("rule"), selected, List.of(action("action", "flow", false)), "flow");
        var runtime = new ActionRuntime(registry, new CustomActionRegistry());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var pending = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                String override = i % 2 == 0 ? "action" : null;
                pending.add(executor.submit(() -> {
                    var model = new ModelContext(Map.of());
                    var result = runtime.execute(action(override, "flow", false), new ActionExecutionContext(
                            engine(registry), model, Map.of("dataSource", "payload-must-not-route"), Instant.MAX));
                    assertEquals(ActionStatus.SUCCESS, result.status(), result.error());
                    assertEquals(override == null ? "rule" : "action", model.read("actualSource"));
                    return null;
                }));
            }
            for (var future : pending) future.get();
        }
    }

    @Test void customActionGetsSelectedAdapterAndDefaultOrOverride() throws Exception {
        var calls = new ArrayList<String>(); var selected = selection(calls); var registry = new RuleViewRegistry();
        var custom = CustomActionRegistry.from(List.of(new CustomAction() {
            public String name() { return "route"; }
            public void execute(ActionExecutionContext c) {
                assertSame(selected.adapter(c.dataSource()), c.dataSourceAdapter());
                calls.add(c.dataSource());
            }
        }));
        var runtime = new ActionRuntime(registry, custom, ActionListenerRegistry.empty(), Clock.systemUTC(), null, selected);
        for (String override : new String[]{null, "action"}) {
            var result = runtime.execute(action(override, "flow", true), engine(registry), new ModelContext(Map.of()));
            assertEquals(ActionStatus.SUCCESS, result.status(), result.error());
        }
        assertEquals(List.of("flow", "action"), calls);
    }
    @Test void parserCarriesBusinessAndBackOverridesAndRejectsUnknownLogicalNames() {
        var back = Map.of("id", "ACT-BACK", "name", "back", "dataSource", "audit");
        var business = new LinkedHashMap<String,Object>(Map.of("id", "BUS", "name", "flow", "dataSource", "db",
                "directories", List.of(Map.of("name", "root", "actions", List.of(Map.of("id", "ACT", "name", "work")),
                        "subDirectories", List.of(Map.of("rel", "child", "back", Map.of("actions", List.of(back))))))));
        var config = new DecDocument(DecKind.CONFIG, "dec/v1", Path.of("config.yaml"), Map.of("dataSources", List.of(
                Map.of("name", "db"), Map.of("name", "audit"))), List.of());
        var project = new DecProject(); project.addDocument(config);
        project.addDocument(new DecDocument(DecKind.BUSINESS, "dec/v1", Path.of("flow.yaml"), Map.of("business", business), List.of()));
        var parsed = new BusinessActionParser().parse(project);
        assertEquals("db", parsed.get(0).defaultDataSource());
        assertNull(parsed.get(0).dataSource());
        assertEquals("audit", parsed.get(1).dataSource());
        assertEquals("db", parsed.get(1).businessDataSource());
        assertEquals("root.back.child", parsed.get(1).ownerDirectory());
        business.put("dataSource", "missing");
        var invalid = new DecProject(); invalid.addDocument(config);
        invalid.addDocument(new DecDocument(DecKind.BUSINESS, "dec/v1", Path.of("flow.yaml"), Map.of("business", business), List.of()));
        assertThrows(ActionExecutionException.class, () -> new BusinessActionParser().parse(invalid));
    }
}
