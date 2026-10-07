package com.dec.lite.query;

import com.dec.lite.directory.DirectoryGraph;
import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.TransactionPolicy;
import com.dec.lite.runtime.RuntimeErrorCode;
import com.dec.lite.parser.DecYamlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryCompilerTest {
    @Test void sessionQueryFailureRecordsStableCodeAndTrace() throws Exception {
        Fixture fixture = fixture();
        QueryPlan plan = fixture.compiler().compile(DirectoryQuery.find("PayResult").page(0, 1));
        InformationEngine information = new InformationEngine(new InformationEngineContext(fixture.information(), new RuleViewRegistry()));
        try (ExecutionSession session = new ExecutionSession(information,
                new ActionRuntime(new RuleViewRegistry(), new CustomActionRegistry()),
                new ModelContext(Map.of()), Map.of(), Instant.MAX, null, TransactionPolicy.NONE, null)) {
            QueryExecutor failing = (sql, route) -> { throw new QueryExecutionException("database unavailable"); };
            assertThrows(QueryExecutionException.class,
                    () -> new QueryRunner(failing, new SqlTranslator(new MySqlDialect()), session).run(plan));
            assertEquals(RuntimeErrorCode.QUERY, session.errors().get(0).code());
            assertEquals("PayResult", session.errors().get(0).entityKey());
            assertTrue(session.errors().get(0).trace().stream().anyMatch(event -> event.phase().equals("QUERY_STARTED")));
        }
    }
    private static Fixture fixture() throws Exception {
        Path path = Path.of(QueryCompilerTest.class.getClassLoader().getResource("mix").toURI());
        DecProject project = new DecYamlParser().parse(path);
        InformationCompilation information = new InformationParser().parse(project);
        DirectoryGraph graph = new DirectoryGraphCompiler().compile(project, information);
        return new Fixture(project, information, graph, new QueryCompiler(project, graph, information));
    }
    @Test void resultUnionAndCaseFilterUseOnlyCaseEdges() throws Exception {
        Fixture fixture = fixture();
        QueryPlan union = fixture.compiler().compile(DirectoryQuery.find("PayResult"));
        assertEquals(List.of("success", "error"), union.selectedCases());
        assertTrue(union.runtimeOnly());
        assertFalse(union.selectedCases().contains("paying"));
        QueryPlan selected = fixture.compiler().compile(DirectoryQuery.find("PayResult").eq("success").from("paying").to("success"));
        assertEquals(List.of("success"), selected.selectedCases());
        assertThrows(QueryCompilationException.class, () -> fixture.compiler().compile(DirectoryQuery.find("PayResult").eq("paying")));
        assertThrows(QueryCompilationException.class, () -> fixture.compiler().compile(DirectoryQuery.find("paying").eq("success")));
    }
    @Test void joinsRouteAndParameterizationAreDerivedFromMetadata() throws Exception {
        QueryPlan plan = fixture().compiler().compile(DirectoryQuery.find("PayResult").with("user").with("orderDetailList")
                .whereSensitive("user.name", "3' OR 1=1 --").whereIn("id", List.of(1, 2)).page(0, 20));
        assertEquals("OrderInfo", plan.rootModel());
        assertEquals("data1", plan.route().dataSource());
        assertEquals("con1", plan.route().connection());
        assertEquals(List.of("user", "orderDetailList"), plan.joins().stream().map(JoinPlan::path).toList());
        assertTrue(plan.joins().get(1).many());
        assertEquals(List.of("orderDetailList"), plan.assembly().collectionPaths());
        SqlTranslator mysql = new SqlTranslator(new MySqlDialect());
        SqlStatement sql = mysql.candidates(plan, 0, 20);
        assertTrue(sql.sql().contains("LEFT JOIN `user_info`"));
        assertTrue(sql.sql().contains("LEFT JOIN `order_detail_info`"));
        assertTrue(sql.sql().contains(" IN (?, ?)"));
        assertFalse(sql.sql().contains("OR 1=1"));
        assertEquals("3' OR 1=1 --", sql.parameters().get(0).value());
        assertTrue(sql.parameters().get(0).sensitive());
        assertFalse(mysql.explain(plan).contains("3' OR 1=1 --"));
        assertEquals(5, sql.parameters().size());
        assertTrue(mysql.details(plan, List.of(1, 2)).sql().contains("WHERE `t0`.`o_id` IN (?, ?)"));
        assertTrue(new SqlTranslator(new AnsiSqlDialect()).candidates(plan, 0, 20).sql().contains("FETCH NEXT ? ROWS ONLY"));
        assertTrue(mysql.explain(plan).contains("selectedCases=[success, error]"));
        assertThrows(QueryCompilationException.class, () -> fixture().compiler().compile(DirectoryQuery.find("PayResult").with("notExisting")));
    }
    @Test void runtimeOnlyIsBoundedAndModelPredicatePushdownIsConservative() throws Exception {
        QueryCompiler compiler = fixture().compiler();
        QueryPlan paying = compiler.compile(DirectoryQuery.find("paying"));
        assertTrue(paying.joins().stream().anyMatch(join -> join.path().equals("orderDetailList")));
        String sql = new SqlTranslator(new MySqlDialect()).candidates(paying, 0, 10).sql();
        assertTrue(sql.contains("`t0`.`o_status` = ?"), sql);
        assertTrue(paying.runtimeOnly()); // every(orderDetailList) remains a post-filter
        assertThrows(QueryCompilationException.class, () -> compiler.compile(DirectoryQuery.find("PayResult").page(1000, 50)));
        assertThrows(QueryCompilationException.class, () -> compiler.compile(DirectoryQuery.find("PayResult").whereIn("id", List.of())));
    }
    @Test void queryRunnerClassifiesCasesAndAssemblesPagedOneToManyRows() throws Exception {
        Fixture fixture = fixture();
        QueryPlan plan = fixture.compiler().compile(DirectoryQuery.find("PayResult").with("user").with("orderDetailList").page(1, 1));
        RuleViewRegistry registry = new RuleViewRegistry();
        registry.register("payment", "OrderInfo", "hasPayResult", invocation -> truth(invocation.context(), true));
        registry.register("payment", "OrderInfo", "isPaySuccess", invocation -> truth(invocation.context(), 1L));
        registry.register("payment", "OrderInfo", "isPayError", invocation -> truth(invocation.context(), 2L));
        InformationEngine engine = new InformationEngine(new InformationEngineContext(fixture.information(), registry));
        List<SqlStatement> seen = new ArrayList<>();
        QueryExecutor executor = (statement, route) -> {
            seen.add(statement);
            if (statement.sql().startsWith("SELECT DISTINCT")) return List.of(Map.of("__dec_id", 1L), Map.of("__dec_id", 2L));
            return List.of(row(1L, 11L, 101L), row(1L, 11L, 102L), row(2L, 12L, 201L));
        };
        QueryResult result = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), engine).run(plan);
        assertEquals(1, result.models().size());
        assertEquals(2L, result.models().get(0).read("id"));
        assertEquals(12L, result.models().get(0).read("user.id"));
        assertEquals(1, ((List<?>) result.models().get(0).read("orderDetailList")).size());
        assertEquals(2, result.scannedCandidates());
        assertEquals(2, seen.size());
        QueryPlan firstPage = fixture.compiler().compile(DirectoryQuery.find("PayResult").with("orderDetailList").page(0, 1));
        QueryResult first = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), engine).run(firstPage);
        assertEquals(2, ((List<?>) first.models().get(0).read("orderDetailList")).size());
    }
    @Test void runtimeOnlyFailsExplicitlyWhenCandidateCapOrEvaluatorIsMissing() throws Exception {
        Fixture fixture = fixture();
        QueryPlan plan = fixture.compiler().compile(DirectoryQuery.find("PayResult").candidateLimit(1).page(0, 1));
        QueryExecutor executor = (statement, route) -> statement.sql().startsWith("SELECT DISTINCT")
                ? List.of(Map.of("__dec_id", 1L)) : List.of(row(1L, 11L, 101L));
        InformationEngine empty = new InformationEngine(new InformationEngineContext(fixture.information(), new RuleViewRegistry()));
        QueryExecutionException unresolved = assertThrows(QueryExecutionException.class,
                () -> new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), empty).run(plan));
        assertTrue(unresolved.getMessage().contains("UNRESOLVED"));
        RuleViewRegistry falseRegistry = new RuleViewRegistry();
        falseRegistry.register("payment", "OrderInfo", "hasPayResult", invocation -> truth(invocation.context(), false));
        InformationEngine falseEngine = new InformationEngine(new InformationEngineContext(fixture.information(), falseRegistry));
        QueryExecutionException capped = assertThrows(QueryExecutionException.class,
                () -> new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), falseEngine).run(plan));
        assertTrue(capped.getMessage().contains("candidateLimit"));
    }
    @Test void missingRouteAndUnsafeOrPredicateDoNotProduceAnUnsafePlan() throws Exception {
        Path original = Path.of(QueryCompilerTest.class.getClassLoader().getResource("mix").toURI());
        Path missingRoute = Files.createTempDirectory("dec-query-route-");
        copyFixture(original, missingRoute);
        Files.writeString(missingRoute.resolve("orm-config.yaml"), Files.readString(original.resolve("orm-config.yaml")).replace("    - data1", "    - absent"));
        DecProject noRoute = new DecYamlParser().parse(missingRoute);
        InformationCompilation information = new InformationParser().parse(noRoute);
        DirectoryGraph graph = new DirectoryGraphCompiler().compile(noRoute, information);
        assertThrows(QueryCompilationException.class, () -> new QueryCompiler(noRoute, graph, information).compile(DirectoryQuery.find("PayResult")));

        Path disjunction = Files.createTempDirectory("dec-query-or-");
        copyFixture(original, disjunction);
        Files.writeString(disjunction.resolve("systems-order.yaml"), Files.readString(original.resolve("systems-order.yaml"))
                .replace("status = 2\n          and\n          every", "status = 2\n          or\n          every"));
        DecProject project = new DecYamlParser().parse(disjunction);
        InformationCompilation compiled = new InformationParser().parse(project);
        QueryPlan plan = new QueryCompiler(project, new DirectoryGraphCompiler().compile(project, compiled), compiled)
                .compile(DirectoryQuery.find("paying"));
        assertFalse(new SqlTranslator(new MySqlDialect()).candidates(plan, 0, 10).sql().contains("`t0`.`o_status` = ?"));
    }
    @Test void typedParametersAndJdbcAdapterUsePreparedBinding() {
        TypedParameter date = new TypedParameter("date", "date", "2026-10-07", false);
        TypedParameter enumValue = new TypedParameter("status", "string", Status.SUCCESS, true);
        assertEquals(LocalDate.of(2026, 10, 7), date.value());
        assertEquals("SUCCESS", enumValue.value());
        assertFalse(enumValue.toString().contains("SUCCESS"));
        List<Object> bound = new ArrayList<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setObject")) bound.add(args[1]);
                    if (method.getName().equals("executeUpdate")) return 1;
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement") ? statement : null);
        JdbcQueryExecutor jdbc = new JdbcQueryExecutor(route -> connection);
        assertEquals(1, jdbc.execute(new SqlStatement("UPDATE t SET d=?, s=?", List.of(date, enumValue)),
                new ConnectionRoute("test", "data1", "MySQL")));
        assertEquals(List.of(LocalDate.of(2026, 10, 7), "SUCCESS"), bound);
    }
    private static void copyFixture(Path from, Path to) throws Exception {
        try (var files = Files.list(from)) {
            for (Path file : files.toList()) Files.copy(file, to.resolve(file.getFileName()));
        }
    }
    private enum Status { SUCCESS }
    private static RecognitionResult truth(ModelContext model, Object expected) {
        boolean value = expected instanceof Boolean flag ? flag : expected.equals(model.read("id"));
        return RecognitionResult.of(value ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, model.version(), "test");
    }
    private static Map<String, Object> row(Long orderId, Long userId, Long detailId) {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("__dec_id", orderId);
        row.put(ViewJoinPlanner.resultAlias("id"), orderId);
        row.put(ViewJoinPlanner.resultAlias("user.__dec_id"), userId);
        row.put(ViewJoinPlanner.resultAlias("user.id"), userId);
        row.put(ViewJoinPlanner.resultAlias("orderDetailList.__dec_id"), detailId);
        row.put(ViewJoinPlanner.resultAlias("orderDetailList.id"), detailId);
        return row;
    }
    private record Fixture(DecProject project, InformationCompilation information, DirectoryGraph graph, QueryCompiler compiler) { }
}
