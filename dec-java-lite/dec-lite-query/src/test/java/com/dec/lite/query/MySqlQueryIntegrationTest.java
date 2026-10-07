package com.dec.lite.query;

import com.dec.lite.directory.DirectoryGraphCompiler;
import com.dec.lite.information.*;
import com.dec.lite.model.DecProject;
import com.dec.lite.parser.DecYamlParser;
import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.session.ExecutionSession;
import com.dec.lite.session.TransactionPolicy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a disposable MySQL database when DEC_MYSQL_JDBC_URL is supplied. */
class MySqlQueryIntegrationTest {
    @Test void twoSystemsShareOneConnectionAndFailureRollsBackBothWrites() throws Exception {
        String url = mysqlUrl();
        String user = System.getenv().getOrDefault("DEC_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("DEC_MYSQL_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, user, password); Statement sql = connection.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS dec_p7_tx");
            sql.execute("CREATE TABLE dec_p7_tx (id INT PRIMARY KEY, system_name VARCHAR(40))");
        }
        AtomicInteger opens = new AtomicInteger();
        ConnectionRouter router = route -> { opens.incrementAndGet(); return DriverManager.getConnection(url, user, password); };
        InformationEngine information = new InformationEngine(new InformationParser().parse(
                new DecYamlParser().parse(Path.of(getClass().getClassLoader().getResource("mix").toURI()))));
        try (ExecutionSession session = new ExecutionSession(information,
                new ActionRuntime(new RuleViewRegistry(), new CustomActionRegistry()), new ModelContext(Map.of()), Map.of(),
                Instant.MAX, null, TransactionPolicy.REQUIRED, null)) {
            SessionJdbcQueryExecutor executor = new SessionJdbcQueryExecutor(session, router);
            ConnectionRoute route = new ConnectionRoute("con1", "data1", "MySQL");
            assertThrows(IllegalStateException.class, () -> {
                try (var scope = session.begin()) {
                    executor.execute(new SqlStatement("INSERT INTO dec_p7_tx VALUES (1, 'order')", java.util.List.of()), route);
                    executor.execute(new SqlStatement("INSERT INTO dec_p7_tx VALUES (2, 'payment')", java.util.List.of()), route);
                    throw new IllegalStateException("payment failed");
                }
            });
            assertEquals(1, opens.get());
            assertEquals(com.dec.lite.session.TransactionCoordinator.State.ROLLED_BACK, session.transactions().state());
            assertTrue(session.transactions().events().stream().anyMatch(event -> event.outcome().equals("ROLLED_BACK")));
        }
        try (var connection = DriverManager.getConnection(url, user, password); Statement sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT COUNT(*) FROM dec_p7_tx")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }
    @Test void mysqlExecutesParameterizedCaseQueryWithDistinctRootPagingAndAssembly() throws Exception {
        String url = mysqlUrl();
        String user = System.getenv().getOrDefault("DEC_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("DEC_MYSQL_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, user, password); Statement sql = connection.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS order_detail_info");
            sql.execute("DROP TABLE IF EXISTS order_info");
            sql.execute("DROP TABLE IF EXISTS user_info");
            sql.execute("CREATE TABLE user_info (u_id BIGINT PRIMARY KEY, u_name VARCHAR(80), u_active INT, u_certified INT)");
            sql.execute("CREATE TABLE order_info (o_id BIGINT PRIMARY KEY, o_user_id BIGINT, o_product_count INT, o_total_price DECIMAL(12,2), o_total_amount DECIMAL(12,2), o_status INT, o_pay_id BIGINT, o_date TIMESTAMP NULL)");
            sql.execute("CREATE TABLE order_detail_info (od_id BIGINT PRIMARY KEY, od_order_id BIGINT, od_user_id BIGINT, od_product_id BIGINT, od_product_name VARCHAR(80), od_product_price DECIMAL(12,2), od_product_amount DECIMAL(12,2), od_status INT)");
            sql.execute("INSERT INTO user_info (u_id,u_name,u_active,u_certified) VALUES (11,'A',1,1),(12,'B',1,1)");
            sql.execute("INSERT INTO order_info (o_id,o_user_id,o_status) VALUES (1,11,3),(2,12,4)");
            sql.execute("INSERT INTO order_detail_info (od_id,od_order_id,od_status) VALUES (101,1,3),(102,1,3),(201,2,4)");
        }
        Path yaml = Path.of(getClass().getClassLoader().getResource("mix").toURI());
        DecProject project = new DecYamlParser().parse(yaml);
        InformationCompilation compiled = new InformationParser().parse(project);
        var graph = new DirectoryGraphCompiler().compile(project, compiled);
        QueryPlan plan = new QueryCompiler(project, graph, compiled).compile(
                DirectoryQuery.find("PayResult").with("user").with("orderDetailList").page(1, 1));
        RuleViewRegistry registry = new RuleViewRegistry();
        registry.register("payment", "OrderInfo", "hasPayResult", invocation -> recognized(invocation.context(), true));
        registry.register("payment", "OrderInfo", "isPaySuccess", invocation -> recognized(invocation.context(), 1L));
        registry.register("payment", "OrderInfo", "isPayError", invocation -> recognized(invocation.context(), 2L));
        InformationEngine engine = new InformationEngine(new InformationEngineContext(compiled, registry));
        AtomicInteger opens = new AtomicInteger();
        ConnectionRouter router = route -> { opens.incrementAndGet(); return DriverManager.getConnection(url, user, password); };
        QueryResult result = new QueryRunner(new JdbcQueryExecutor(router), new SqlTranslator(new MySqlDialect()), engine).run(plan);
        assertEquals(1, result.models().size());
        assertEquals(2L, ((Number) result.models().get(0).read("id")).longValue());
        assertEquals("B", result.models().get(0).read("user.name"));
        assertEquals(1, ((java.util.List<?>) result.models().get(0).read("orderDetailList")).size());
        int beforeSession = opens.get();
        try (ExecutionSession session = new ExecutionSession(engine,
                new ActionRuntime(registry, new CustomActionRegistry()), new ModelContext(Map.of()), Map.of(),
                Instant.MAX, null, TransactionPolicy.REQUIRED, null);
             var scope = session.begin()) {
            QueryResult first = new QueryRunner(new SessionJdbcQueryExecutor(session, router),
                    new SqlTranslator(new MySqlDialect()), session).run(
                    new QueryCompiler(project, graph, compiled).compile(DirectoryQuery.find("PayResult").with("orderDetailList").page(0, 1)));
            assertEquals(2, ((java.util.List<?>) first.models().get(0).read("orderDetailList")).size());
            assertEquals(1, opens.get() - beforeSession); // candidate and detail share one connection
            scope.commit();
            assertTrue(session.transactions().events().stream().anyMatch(event -> event.outcome().equals("COMMITTED")));
        }
    }
    private static RecognitionResult recognized(ModelContext context, Object expected) {
        boolean matched = expected instanceof Boolean value ? value : ((Number) context.read("id")).longValue() == ((Number) expected).longValue();
        return RecognitionResult.of(matched ? RecognitionResult.Status.TRUE : RecognitionResult.Status.FALSE, context.version(), "mysql-test");
    }
    private static String mysqlUrl() {
        String url = System.getenv("DEC_MYSQL_JDBC_URL");
        if (Boolean.getBoolean("dec.mysql.it.required")) {
            assertNotNull(url, "mysql-it profile requires DEC_MYSQL_JDBC_URL");
            assertFalse(url.isBlank(), "mysql-it profile requires DEC_MYSQL_JDBC_URL");
        } else Assumptions.assumeTrue(url != null && !url.isBlank(), "set DEC_MYSQL_JDBC_URL for MySQL integration");
        return url;
    }
}
