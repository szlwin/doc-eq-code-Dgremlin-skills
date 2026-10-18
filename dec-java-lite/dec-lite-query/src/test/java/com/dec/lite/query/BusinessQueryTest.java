package com.dec.lite.query;

import com.dec.lite.directory.*;
import com.dec.lite.information.*;
import com.dec.lite.model.*;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BusinessQueryTest {
    static BusinessQueryNames names() {
        return new BusinessQueryNames(Map.of("订单", "VIEW-ORDER-INFO"),
                Map.of("已订购", "DIR-ORDERED", "支付中", "paying", "支付结果", "PayResult", "支付成功", "success", "支付失败", "error"),
                Map.of("OrderInfo", Map.of("用户", "user", "明细", "orderDetailList")));
    }
    record Fixture(DecProject project, DirectoryGraph graph, InformationCompilation information, QueryCompiler compiler) {
        QueryResult run(BusinessQuery query) {
            QueryExecutor executor = (statement, route) -> statement.sql().startsWith("SELECT DISTINCT")
                    ? List.of(Map.of("__dec_id", 1L), Map.of("__dec_id", 2L), Map.of("__dec_id", 3L), Map.of("__dec_id", 4L), Map.of("__dec_id", 5L))
                    : List.of(row(1, 1), row(2, 2), row(3, 3), row(4, 4), row(5, 9));
            return new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), new InformationEngine(information)).run(compiler.compile(query));
        }
    }
    /** Existing canonical mappings/flow, with test Information declared as model predicates rather than provider callbacks. */
    static Fixture fixture(int successStatus) throws Exception {
        DecProject source = new DecYamlParser().parse(Path.of(BusinessQueryTest.class.getClassLoader().getResource("mix").toURI()));
        DecProject project = new DecProject();
        Map<String, String> expressions = Map.of("order.ordered", "status = 1", "order.paying", "status = 2",
                "payment.hasResult", "status = " + successStatus + " or status = 4", "payment.success", "status = " + successStatus,
                "payment.error", "status = 4", "order.paySuccessStatus", "status = " + successStatus, "order.payErrorStatus", "status = 4");
        for (var document : source.getDocuments()) {
            if (document.kind() != DecKind.SYSTEMS) { project.addDocument(document); continue; }
            Map<String, Object> root = new LinkedHashMap<>(document.root()); List<Object> systems = new ArrayList<>();
            for (Object raw : QuerySchema.list(root.get("systems"), "systems")) {
                Map<String, Object> system = QuerySchema.map(raw, "system"); List<Object> definitions = new ArrayList<>();
                for (Object item : QuerySchema.list(system.get("information"), "information")) {
                    Map<String, Object> definition = QuerySchema.map(item, "information");
                    String expression = expressions.get(system.get("name") + "." + definition.get("name"));
                    if (expression != null) {
                        definition.remove("ruleRef"); definition.remove("changeData");
                        definition.put("viewRef", "OrderInfo"); definition.put("ruleData", expression);
                    }
                    definitions.add(definition);
                }
                system.put("information", definitions); systems.add(system);
            }
            root.put("systems", systems);
            project.addDocument(new DecDocument(document.kind(), document.version(), document.source(), root, document.nodes()));
        }
        var information = new InformationParser().parse(project);
        var graph = new DirectoryGraphCompiler().compile(project, information);
        return new Fixture(project, graph, information, new QueryCompiler(project, graph, information, names()));
    }
    @Test void articleExamplesSelectCurrentStagesAndLoadUsers() throws Exception {
        Fixture fixture = fixture(3);
        assertEquals(List.of(3L, 4L), ids(fixture.run(BusinessQuery.find("订单").eq("支付结果"))));
        assertEquals(List.of(1L, 2L, 3L, 4L), ids(fixture.run(BusinessQuery.find("订单").start("已订购"))));
        BusinessQuery range = BusinessQuery.find("订单").start("已订购").end("支付成功");
        assertEquals(List.of(1L, 2L, 3L), ids(fixture.run(range)));
        var withUser = fixture.run(range.with("用户"));
        assertEquals(List.of(1L, 2L, 3L), ids(withUser));
        assertEquals("user-3", withUser.models().get(2).read("user.name"));
        assertFalse(fixture.compiler().compile(range).selectedCases().contains("error"));
        assertTrue(fixture.compiler().compile(range).trace().contains("queryMode=CURRENT_BUSINESS_STAGES"));
    }
    @Test void endpointModesAndPaginationAreExplicit() throws Exception {
        Fixture fixture = fixture(3);
        assertEquals(List.of(3L), ids(fixture.run(BusinessQuery.find("订单").eq("支付成功"))));
        assertEquals(List.of(3L), ids(fixture.run(BusinessQuery.find("订单").start("支付成功").end("支付成功"))));
        assertEquals(List.of(1L, 2L, 3L), ids(fixture.run(BusinessQuery.find("订单").end("支付成功"))));
        assertEquals(List.of(2L, 3L, 4L), ids(fixture.run(BusinessQuery.find("订单").start("支付中").end("支付结果"))));
        assertEquals(List.of(3L, 4L), ids(fixture.run(BusinessQuery.find("订单").start("支付结果"))));
        assertEquals(List.of(2L, 3L), ids(fixture.run(BusinessQuery.find("订单").page(1, 2))));
    }
    @Test void invalidSelectorsAndAliasesFailBeforeAnyIo() throws Exception {
        Fixture f = fixture(3);
        assertThrows(IllegalArgumentException.class, () -> BusinessQuery.find("订单").eq("支付结果").start("已订购"));
        assertThrows(IllegalArgumentException.class, () -> BusinessQuery.find("订单").end("支付成功").eq("支付结果"));
        assertThrows(IllegalArgumentException.class, () -> BusinessQuery.find("订单").start(" "));
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("missing")));
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("订单").start("missing")));
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("订单").start("支付成功").end("已订购")));
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("订单").start("支付失败").end("支付成功")));
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("UserInfo").eq("支付结果")));
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("订单").with("missing")));
        assertThrows(QueryCompilationException.class, () -> new QueryCompiler(f.project(), f.graph(), f.information(),
                new BusinessQueryNames(Map.of("OrderInfo", "UserInfo"), Map.of(), Map.of())));
        assertThrows(QueryCompilationException.class, () -> new QueryCompiler(f.project(), f.graph(), f.information(),
                new BusinessQueryNames(Map.of(), Map.of("paid", "missing"), Map.of())));
        assertThrows(QueryCompilationException.class, () -> new QueryCompiler(f.project(), f.graph(), f.information(),
                new BusinessQueryNames(Map.of(), Map.of(), Map.of("OrderInfo", Map.of("user", "orderDetailList")))));
    }
    @Test void designChangesDriveResultsAndSqlWithoutChangingCallers() throws Exception {
        BusinessQuery query = BusinessQuery.find("订单").eq("支付成功");
        assertEquals(List.of(3L), ids(fixture(3).run(query)));
        Fixture changed = fixture(9);
        assertEquals(List.of(5L), ids(changed.run(query)));
        QueryPlan plan = changed.compiler().compile(query);
        assertTrue(plan.parameters().stream().anyMatch(parameter -> ((Number) parameter.value()).intValue() == 9));
        // Removing the failed case changes the open range as well, without a Java status list.
        Map<String, DirectoryDefinition> remaining = new LinkedHashMap<>(changed.graph().directories()); remaining.remove("error");
        DirectoryGraph graph = new DirectoryGraph(remaining, changed.graph().executionEdges(),
                changed.graph().caseEdges().stream().filter(edge -> !edge.target().equals("error")).toList(), List.of(), changed.graph().root());
        QueryPlan narrowed = new QueryCompiler(changed.project(), graph, changed.information())
                .compile(BusinessQuery.find("OrderInfo").start("ordered"));
        assertFalse(narrowed.selectedCases().contains("error"));
    }
    @Test void canonicalIdsAndNamesAreEquivalentAndBuildersAreImmutable() throws Exception {
        Fixture fixture = fixture(3);
        BusinessQuery base = BusinessQuery.find("VIEW-ORDER-INFO");
        var first = fixture.compiler().compile(base.eq("DIR-SUCCESS"));
        var second = fixture.compiler().compile(BusinessQuery.find("OrderInfo").eq("success"));
        assertEquals(first.predicate(), second.predicate());
        assertNull(base.stage()); assertTrue(base.options().with().isEmpty());
        assertEquals(List.of(1L, 2L, 3L, 4L), ids(fixture.run(base)));
    }
    @Test void overlappingOrdinaryStagesDeduplicateButResultCasesRemainExclusive() throws Exception {
        Fixture fixture = fixture(3);
        var branches = List.of(
                new QueryPlan.CaseBranch("stage-a", List.of(InformationKey.parse("order.ordered")), SqlPredicate.all(), true, null),
                new QueryPlan.CaseBranch("stage-b", List.of(InformationKey.parse("order.ordered")), SqlPredicate.all(), true, null));
        QueryPlan original = fixture.compiler().compile(BusinessQuery.find("订单").eq("已订购"));
        QueryPlan union = replaceBranches(original, branches);
        QueryExecutor executor = (sql, route) -> sql.sql().startsWith("SELECT DISTINCT") ? List.of(Map.of("__dec_id", 1L)) : List.of(row(1, 1));
        QueryRunner runner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), new InformationEngine(fixture.information()));
        assertEquals(List.of(1L), ids(runner.run(union)));
        var exclusive = branches.stream().map(b -> new QueryPlan.CaseBranch(b.name(), b.information(), b.predicate(), b.postFilter(), "same-result")).toList();
        assertThrows(QueryExecutionException.class, () -> runner.run(replaceBranches(original, exclusive)));
    }
    @Test void backEdgesDoNotBecomeQueryForwardRangesAndStateIsNotHistory() throws Exception {
        Fixture f = fixture(3);
        assertFalse(f.graph().backEdges().isEmpty());
        assertThrows(QueryCompilationException.class, () -> f.compiler().compile(BusinessQuery.find("订单").start("支付结果").end("支付中")));
        // A record reset to status 1 has no evidence of its prior payment in this snapshot.
        assertFalse(ids(f.run(BusinessQuery.find("订单").eq("支付结果"))).contains(1L));
    }
    static List<Long> ids(QueryResult result) { return result.models().stream().map(model -> ((Number) model.read("id")).longValue()).toList(); }
    private static Map<String, Object> row(long id, int status) {
        return Map.of("__dec_id", id, "id__dec", id, "status__dec", status,
                "user___dec_id__dec", 10L + id, "user_id__dec", 10L + id, "user_name__dec", "user-" + id);
    }
    private static QueryPlan replaceBranches(QueryPlan p, List<QueryPlan.CaseBranch> branches) {
        return new QueryPlan(p.directory(), p.rootModel(), p.rootTable(), p.rootIdColumn(), p.projections(), p.joins(), p.predicate(),
                branches, p.selectedCases(), p.parameters(), p.orderBy(), p.offset(), p.size(), p.candidateLimit(), p.assembly(), p.route(), p.runtimeOnly(), p.trace());
    }
}
