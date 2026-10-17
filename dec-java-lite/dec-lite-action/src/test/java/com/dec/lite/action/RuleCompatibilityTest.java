package com.dec.lite.action;

import com.dec.lite.information.*;
import com.dec.lite.model.*;
import com.dec.lite.parser.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleCompatibilityTest {
    private static Path resource(String name) throws Exception {
        return Path.of(RuleCompatibilityTest.class.getClassLoader().getResource(name).toURI());
    }
    private static ModelContext model() {
        return new ModelContext(Map.of("id", 10L, "userId", 1L, "productCount", 2L, "totalPrice", 20L,
                "userT", Map.of("id", 1L, "userName", "Alice", "uname", "Alice"),
                "productList", List.of(Map.of("id", 101L, "productPrice", 20L, "orderId", 10L),
                        Map.of("id", 102L, "productPrice", 30L, "orderId", 10L))));
    }
    private static CompiledRule rule(String type, String property, String pattern, String process) {
        return new CompiledRule("RULE-" + type, type, type, property, pattern, process, "db");
    }
    private static RuleViewRegistry registry(DataOperationAdapter adapter, CompiledRule... rules) {
        var key = new RuleViewKey("compatibility", "OrderInfo", "test");
        return new RuleViewCompiler().bind(Map.of(key, new CompiledRuleView(key, "RV", List.of(rules))), adapter);
    }
    private static RecognitionResult invoke(RuleViewRegistry registry, ModelContext model) {
        return registry.invoke("compatibility", "test", model, Map.of(), new MutationSet());
    }
    private static void success(RecognitionResult result) { assertEquals(RecognitionResult.Status.TRUE, result.status(), result.errors().toString()); }

    /** Test DataSource works by operation/property, not business Rule IDs. */
    private static final class MemoryDriver implements RuleDataAccess {
        Map<String,Object> user, order;
        final List<Map<String,Object>> products = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        public Result execute(Request request) {
            var rule = request.rule(); String property = rule.property();
            calls.add(request.operation() + ":" + property);
            if (rule.type().equals("checkDataPattern")) {
                assertEquals("select a.id as u_id from userT a where a.id = #userT.id", rule.cmd());
                return Result.read(user == null ? List.of() : List.of(Map.of("u_id", user.get("id"))));
            }
            return switch (request.operation()) {
                case "insert" -> {
                    if (property.equals("userT")) user = copy(request.target());
                    else if (property.equals("OrderInfo")) order = copy(request.target());
                    else if (property.equals("productList")) for (Object row : (List<?>) request.target()) products.add(copy(row));
                    else throw new AssertionError(property);
                    yield Result.written(1);
                }
                case "get" -> {
                    Map<String,Object> value = property.equals("userT") ? user : order;
                    yield Result.read(value == null ? List.of() : List.of(value));
                }
                case "query" -> Result.read(products);
                case "delete" -> {
                    assertEquals("delete p.* from productList p where p.productPrice = 20", rule.cmd());
                    int size = products.size(); products.removeIf(row -> RuleExpression.equal(20L, row.get("productPrice")));
                    yield Result.written(size - products.size());
                }
                case "update" -> { user = copy(request.target()); yield Result.written(1); }
                default -> throw new AssertionError(request.operation());
            };
        }
        private static Map<String,Object> copy(Object value) {
            Map<String,Object> result = new LinkedHashMap<>(); ((Map<?,?>) value).forEach((k,v) -> result.put(String.valueOf(k),v));
            return new ModelContext(result).values();
        }
    }
    @Test void originalRuleExampleExecutesFromBothCanonicalYamlAndGeneratedXml() throws Exception {
        for (DecProject project : List.of(new DecYamlParser().parse(resource("rule-compatibility")),
                new DecXmlParser().parse(resource("rule-compatibility-xml")))) {
            var driver = new MemoryDriver(); var model = model();
            var registry = new RuleViewCompiler().register(project, new RuleDataAdapter(driver));
            success(registry.invoke("compatibility", "save-Order", model, Map.of(), new MutationSet()));
            assertEquals(0, new BigDecimal("440").compareTo(new BigDecimal(model.read("totalPrice").toString())));
            assertFalse(model.values().containsKey("#num")); assertFalse(model.values().containsKey("u_id"));
            assertEquals(1, driver.products.size()); assertEquals(30L, driver.products.get(0).get("productPrice"));
            success(registry.invoke("compatibility", "get-user", model, Map.of(), new MutationSet()));
            assertEquals(1, ((List<?>) model.read("productList")).size());
            assertTrue(driver.calls.contains("get:userT")); assertTrue(driver.calls.contains("query:userT"));
            assertTrue(driver.calls.contains("query:productList")); assertTrue(driver.calls.contains("delete:null"));
        }
    }
    @Test void compilerPreservesCommandCustomerErrorAndNormalizesLegacyAliases() {
        var project = new DecProject();
        var raw = Map.<String,Object>of("id", "R", "name", "read", "type", "query", "property", "items", "sql", "select #id",
                "error", Map.of("code", "C001", "message", "user error", "level", 1), "customer", Map.of("mode", "strict"));
        project.addDocument(new DecDocument(DecKind.RULE, "dec/v1", Path.of("rules.yaml"), Map.of("ruleViews", List.of(Map.of(
                "id", "RV", "name", "rv", "viewRef", "V", "system", "s", "rules", List.of(raw)))), List.of()));
        var compiled = new RuleViewCompiler().compile(project).values().iterator().next().rules().get(0);
        assertEquals("select #id", compiled.cmd()); assertEquals("1", compiled.error().level());
        assertEquals("C001", compiled.error().code());
        assertEquals("strict", compiled.customer().get("mode"));
        assertThrows(UnsupportedOperationException.class, () -> compiled.customer().put("mode", "changed"));
        assertEquals(compiled.customer(), compiled.withDataSource("other").customer());
        assertEquals(compiled.cmd(), compiled.withDataSource("other").cmd());
    }
    @Test void compoundChecksFieldComparisonsAndArithmeticEvaluateBothTrueAndFalse() {
        var model = model();
        success(invoke(registry(null, rule("check", "userId", "NOTNULL;NOTEQUAL:2", null),
                rule("checkPattern", null, "userT.userName = userT.uname and productCount * (totalPrice + 10) >= 60", null)), model));
        model.write("userId", 2L, null);
        assertEquals(RecognitionResult.Status.FALSE, invoke(registry(null, rule("check", "userId", "NOTNULL;NOTEQUAL:2", null)), model).status());
        assertEquals(RecognitionResult.Status.FALSE, invoke(registry(null, rule("checkPattern", null, "productCount * 10 > totalPrice", null)), model).status());
    }
    @Test void dslPreservesDecimalMathShortCircuitQuotedSeparatorsAndVariableIsolation() {
        var rules = registry(null, rule("dsl", null, null,
                "#num : if totalPrice>10 then totalPrice*1.1 else 1/0; totalPrice : totalPrice*#num; note : 'x;y:z'; every(productList, productPrice : productPrice + 1);"));
        var model = model(); success(invoke(rules, model));
        assertEquals("x;y:z", model.read("note"));
        assertEquals(new BigDecimal("21"), model.read("productList[0].productPrice"));
        assertFalse(model.values().containsKey("#num"));
        assertEquals(RecognitionResult.Status.UNRESOLVED, invoke(registry(null, rule("dsl",null,null,"totalPrice : #num;")), model).status());
        success(invoke(registry(null, rule("checkPattern",null,"not (totalPrice < 0) and (true or missing / 0 > 1)",null)),model));
    }
    @Test void checkDataReadsInsteadOfTrustingTheExistingModel() {
        var calls = new ArrayList<String>();
        var adapter = new RuleDataAdapter(request -> { calls.add(request.operation()); return RuleDataAccess.Result.read(List.of()); });
        var model = model();
        assertNotNull(model.read("userT"));
        assertEquals(RecognitionResult.Status.FALSE, invoke(registry(adapter, rule("checkData","userT","NOTNULL",null)),model).status());
        success(invoke(registry(adapter, rule("checkData","userT","NULL",null)),model));
        assertEquals(List.of("get", "get"), calls);
        DataOperationAdapter legacy = (r,i) -> { throw new AssertionError("read must not call execute"); };
        assertEquals(RecognitionResult.Status.ERROR,invoke(registry(legacy,rule("checkData","userT","NOTNULL",null)),model).status());
    }
    @Test void dataPatternChecksAllRowsAndRestoresShadowedAliasesOnSuccessAndFailure() {
        var model = model(); model.write("u_id", 99L, null); var before = model.values();
        var adapter = new RuleDataAdapter(request -> RuleDataAccess.Result.read(List.of(Map.of("u_id",1L),Map.of("u_id",2L))));
        assertEquals(RecognitionResult.Status.FALSE,invoke(registry(adapter,rule("checkDataPattern","userT","u_id = userId",null)),model).status());
        assertEquals(before,model.values());
        var valid = new RuleDataAdapter(request -> RuleDataAccess.Result.read(List.of(Map.of("u_id",1L))));
        success(invoke(registry(valid,rule("checkDataPattern","userT","u_id = userId",null)),model));
        assertEquals(before,model.values());
    }
    @Test void dataValidationRejectsAdapterWrites() {
        DataOperationAdapter adapter = new DataOperationAdapter() {
            public RecognitionResult execute(CompiledRule r,RuleViewRegistry.Invocation i) { throw new AssertionError(); }
            public List<Map<String,Object>> read(CompiledRule r,RuleViewRegistry.Invocation i) {
                i.write("userId", 2L); return List.of(Map.of("id",1L));
            }
        };
        var model = model(); var result=invoke(registry(adapter,rule("checkData","userT","NOTNULL",null)),model);
        assertEquals(RecognitionResult.Status.ERROR,result.status());assertEquals(1L,model.read("userId"));
    }
    @Test void selectedDataSourceIsUsedForDataChecksAndMissingReadRouteFailsBeforeExecution() {
        var seen = new ArrayList<String>();
        var adapter = new RuleDataAdapter(request -> { seen.add(request.rule().dataSource()); return RuleDataAccess.Result.read(List.of(Map.of("id",1L))); });
        var providers = DataSourceAdapterRegistry.from(List.of(new DataSourceAdapter("memory",adapter)));
        var key = new RuleViewKey("compatibility","OrderInfo","test");
        var compiled=Map.of(key,new CompiledRuleView(key,"RV",List.of(rule("checkData","userT","NOTNULL",null))));
        assertThrows(IllegalArgumentException.class,()->new RuleViewCompiler().bind(compiled,providers.select(Map.of())));
        success(invoke(new RuleViewCompiler().bind(compiled,providers.select(Map.of("db","memory"))),model()));
        assertEquals(List.of("db"),seen);
    }
    @Test void genericQueryWritesMutableCollectionsAndGetRejectsMultipleRows() {
        var adapter=new RuleDataAdapter(request->RuleDataAccess.Result.read(List.of(Map.of("productPrice",20L),Map.of("productPrice",30L))));
        var model=model();
        success(invoke(registry(adapter,rule("query","productList",null,null),rule("dsl",null,null,"every(productList, productPrice : productPrice + 1);")),model));
        assertEquals(new BigDecimal("31"),model.read("productList[1].productPrice"));
        assertEquals(RecognitionResult.Status.ERROR,invoke(registry(adapter,rule("get","userT",null,null)),model).status());
    }
    @Test void genericUpdateAndGeneratedInsertKeysUseOperationAndProperty() {
        var driver=new MemoryDriver();var model=model();
        success(invoke(registry(new RuleDataAdapter(driver),rule("update","userT",null,null)),model));
        assertEquals("Alice",driver.user.get("userName"));
        var generated=new RuleDataAdapter(request->new RuleDataAccess.Result(List.of(Map.of("id",201L),Map.of("id",202L)),2));
        success(invoke(registry(generated,rule("insert","productList",null,null)),model));
        assertEquals(201L,model.read("productList[0].id"));assertEquals(20L,model.read("productList[0].productPrice"));
    }
    @Test void ruleErrorIsReportedAndActionRollsBackEarlierDslAndExternalWrites() throws Exception {
        var error = new CompiledRule("R-CHECK","verify","checkData","userT","NOTNULL",null,"db",null,
                new CompiledRule.RuleError("C001","user error","1"),Map.of());
        var driver = new MemoryDriver();
        var adapter = new RuleDataAdapter(request -> request.operation().equals("get") ? RuleDataAccess.Result.read(List.of()) : driver.execute(request));
        var registry=registry(adapter,rule("dsl",null,null,"totalPrice : totalPrice * 2;"),rule("insert","userT",null,null),error);
        var parsed=new DecYamlParser().parse(resource("rule-compatibility"));
        var engine=new InformationEngine(new InformationParser().parse(parsed));
        var tx=new ActionTransaction() { public void rollback(){driver.user=null;} public void commit(){fail("must roll back");} };
        var action=new ActionDefinition("A","test","d","compatibility","test",null,List.of(),null,List.of(),Map.of(),null,Path.of("rules.yaml"));
        var model=model();var before=model.values();
        var result=new ActionRuntime(registry,new CustomActionRegistry()).execute(action,new ActionExecutionContext(engine,model,Map.of(),Instant.MAX,tx));
        assertEquals(ActionStatus.FAILED,result.status());assertTrue(result.error().contains("C001"));assertTrue(result.error().contains("user error"));assertTrue(result.error().contains("level=1"));
        assertNull(driver.user);assertEquals(before,model.values());
    }
    @Test void dataValidationCanRecognizeInformationWithoutModifyingItsModel() {
        var adapter=new RuleDataAdapter(request->RuleDataAccess.Result.read(List.of(Map.of("u_id",1L))));
        var registry=registry(adapter,rule("checkDataPattern","userT","u_id = userId",null));
        var definition=new InformationDefinition("I",new InformationKey("compatibility","exists"),"OrderInfo","test",null,null,null,Path.of("system.yaml"));
        var model=model();var before=model.values();success(registry.invoke(definition,model));assertEquals(before,model.values());
    }
    @Test void unsupportedSyntaxFailsDuringCompilationAndConflictingAliasesAreRejected() {
        for (Map<String,Object> rule : List.of(
                Map.<String,Object>of("id","R","name","r","type","check","property","id","pattern","UNKNOWN"),
                Map.<String,Object>of("id","R","name","r","type","dsl","process","x : items.sum(id);"),
                Map.<String,Object>of("id","R","name","r","type","query","property","items","cmd","one","sql","two"),
                Map.<String,Object>of("id","R","name","r","type","query","property","items","customer",Map.of("nested",Map.of("mode","strict"))),
                Map.<String,Object>of("id","R","name","r","type","dsl","process","x:1;","grammer","x:2;"))) {
            var project=new DecProject();project.addDocument(new DecDocument(DecKind.RULE,"dec/v1",Path.of("r.yaml"),Map.of("ruleViews",List.of(
                    Map.of("id","RV","name","rv","viewRef","V","system","s","rules",List.of(rule)))),List.of()));
            assertThrows(ActionExecutionException.class,()->new RuleViewCompiler().compile(project));
        }
    }
    @Test void malformedExpressionsFailWithRuleDiagnostics() {
        for (String expression : List.of("exists(", "every(items,", "1 +", "if true then 1 else", "(id = 1"))
            assertThrows(ActionExecutionException.class, () -> RuleExpression.compile(expression), expression);
    }
    @Test void sharedRegistryKeepsTemporaryVariablesIsolatedAcrossConcurrentInvocations() throws Exception {
        var rules = registry(null, rule("dsl", null, null, "#num : totalPrice * 2; totalPrice : #num + 1;"));
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < 40; i++) {
                long input = i;
                tasks.add(() -> {
                    var model = new ModelContext(Map.of("totalPrice", input));
                    success(invoke(rules, model));
                    assertEquals(new BigDecimal(input * 2 + 1), model.read("totalPrice"));
                    assertFalse(model.values().containsKey("#num"));
                    return null;
                });
            }
            for (var future : pool.invokeAll(tasks)) future.get();
        }
    }
}
