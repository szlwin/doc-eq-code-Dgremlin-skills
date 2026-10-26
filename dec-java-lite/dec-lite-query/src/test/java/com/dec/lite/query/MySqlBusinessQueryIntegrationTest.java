package com.dec.lite.query;

import com.dec.lite.action.ActionRuntime;
import com.dec.lite.action.CustomActionRegistry;
import com.dec.lite.information.*;
import com.dec.lite.session.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.dec.lite.query.BusinessQueryTest.ids;
import static org.junit.jupiter.api.Assertions.*;

class MySqlBusinessQueryIntegrationTest {
    @Test void articleQueriesExecuteWithRealJoinsPagingBoundValuesAndChangedDesign() throws Exception {
        String url = System.getenv("DEC_MYSQL_JDBC_URL");
        if (Boolean.getBoolean("dec.mysql.it.required")) assertNotNull(url, "mysql-it requires DEC_MYSQL_JDBC_URL");
        else Assumptions.assumeTrue(url != null && !url.isBlank(), "set DEC_MYSQL_JDBC_URL for MySQL integration");
        var fixture = BusinessQueryTest.fixture(3);
        var engine = new InformationEngine(fixture.information());
        try (var session = new ExecutionSession(engine, new ActionRuntime(new RuleViewRegistry(), new CustomActionRegistry()),
                new ModelContext(Map.of()), Map.of(), Instant.MAX, null, TransactionPolicy.REQUIRED, null);
             var scope = session.begin()) {
            var executor = new SessionJdbcQueryExecutor(session, route -> DriverManager.getConnection(url,
                    System.getenv().getOrDefault("DEC_MYSQL_USER", "root"), System.getenv().getOrDefault("DEC_MYSQL_PASSWORD", "")));
            var route = fixture.compiler().compile(BusinessQuery.find("订单")).route();
            for (String sql : List.of(
                    "CREATE TEMPORARY TABLE order_info (o_id BIGINT PRIMARY KEY, o_user_id BIGINT, o_product_count INT, o_total_price DECIMAL(12,2), o_total_amount DECIMAL(12,2), o_status INT, o_pay_id BIGINT, o_date TIMESTAMP NULL)",
                    "CREATE TEMPORARY TABLE user_info (u_id BIGINT PRIMARY KEY, u_name VARCHAR(80), u_active INT, u_certified INT)",
                    "CREATE TEMPORARY TABLE order_detail_info (od_id BIGINT PRIMARY KEY, od_order_id BIGINT, od_user_id BIGINT, od_product_id BIGINT, od_product_name VARCHAR(80), od_product_price DECIMAL(12,2), od_product_amount DECIMAL(12,2), od_status INT)",
                    "INSERT INTO order_info (o_id,o_user_id,o_status) VALUES (1,11,1),(2,12,2),(3,13,3),(4,14,4),(5,15,9)",
                    "INSERT INTO user_info (u_id,u_name) VALUES (11,'用户一'),(12,'用户二'),(13,'用户三'),(14,'用户四')",
                    "INSERT INTO order_detail_info (od_id,od_order_id,od_status) VALUES (101,1,1),(102,1,1),(201,2,2),(301,3,3)"))
                executor.execute(new SqlStatement(sql, List.of()), route);
            var runner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), session);
            assertEquals(List.of(3L, 4L), ids(runner.run(fixture.compiler().compile(BusinessQuery.find("订单").eq("支付结果")))));
            assertEquals(List.of(1L, 2L, 3L, 4L), ids(runner.run(fixture.compiler().compile(BusinessQuery.find("订单").start("已订购")))));
            var range = BusinessQuery.find("订单").start("已订购").end("支付成功");
            assertEquals(List.of(1L, 2L, 3L), ids(runner.run(fixture.compiler().compile(range))));
            var joined = runner.run(fixture.compiler().compile(range.with("用户").with("明细")));
            assertEquals(List.of(1L, 2L, 3L), ids(joined));
            assertEquals("用户三", joined.models().get(2).read("user.name"));
            assertEquals(2, ((List<?>) joined.models().get(0).read("orderDetailList")).size());
            assertEquals(List.of(2L), ids(runner.run(fixture.compiler().compile(range.with("明细").page(1, 1)))));
            assertEquals(List.of(3L), ids(runner.run(fixture.compiler().compile(range.with("用户").where("user.name", "用户三")))));
            assertTrue(runner.run(fixture.compiler().compile(range.where("user.name", "' OR 1=1 --"))).models().isEmpty());
            // The same API resolves a changed Information definition; no status constants are changed in the caller.
            var changed = BusinessQueryTest.fixture(9);
            var changedRunner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), new InformationEngine(changed.information()));
            var newResult = changedRunner.run(changed.compiler().compile(BusinessQuery.find("订单").eq("支付成功").with("用户")));
            assertEquals(List.of(5L), ids(newResult));
            assertNull(newResult.models().get(0).read("user.id")); // LEFT JOIN preserves a missing optional relation.
            // Current-stage query cannot recover a payment after its facts have been reset.
            executor.execute(new SqlStatement("UPDATE order_info SET o_status=1 WHERE o_id=3", List.of()), route);
            assertEquals(List.of(4L), ids(runner.run(fixture.compiler().compile(BusinessQuery.find("订单").eq("支付结果")))));
            // The SQL candidate set must retain strings that Runtime recognizes verbatim.
            for (String value : List.of("a  b", "a\tb", "a and b", "a or b", "a\\'b")) {
                executor.execute(new SqlStatement("UPDATE user_info SET u_name=? WHERE u_id=14",
                        List.of(new TypedParameter("name", "string", value, false))), route);
                var literalFixture = BusinessQueryTest.fixture(3,
                        Map.of("payment.error", "status = 4 and user.name = '" + value + "'"));
                var literalRunner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()),
                        new InformationEngine(literalFixture.information()));
                var plan = literalFixture.compiler().compile(BusinessQuery.find("订单").eq("支付失败").with("用户"));
                assertEquals(List.of(4L), ids(literalRunner.run(plan)), value);
            }
            executor.execute(new SqlStatement("UPDATE user_info SET u_name=NULL WHERE u_id=14", List.of()), route);
            executor.execute(new SqlStatement("UPDATE order_info SET o_total_amount=12.50 WHERE o_id=4", List.of()), route);
            executor.execute(new SqlStatement("INSERT INTO order_detail_info (od_id,od_order_id,od_status) VALUES (401,4,4),(402,4,4)", List.of()), route);
            for (String expression : List.of("status = 4 and user.name = null", "status = 4 and totalAmount = 12.500",
                    "status = 4 and every(orderDetailList, status = 4)", "status = 4 and any(orderDetailList, status = 4)")) {
                var variant = BusinessQueryTest.fixture(3, Map.of("payment.error", expression));
                var variantRunner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), new InformationEngine(variant.information()));
                var plan = variant.compiler().compile(BusinessQuery.find("订单").eq("支付失败").with("用户").with("明细"));
                assertEquals(List.of(4L), ids(variantRunner.run(plan)), expression);
            }
            for (String expression : List.of("status = 4 and user.name = 'null'", "status = 4 and totalAmount = 12.51",
                    "status = 4 and every(orderDetailList, status = 2)", "status = 4 and any(orderDetailList, status = 2)")) {
                var variant = BusinessQueryTest.fixture(3, Map.of("payment.error", expression));
                var variantRunner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), new InformationEngine(variant.information()));
                assertTrue(variantRunner.run(variant.compiler().compile(BusinessQuery.find("订单").eq("支付失败")
                        .with("用户").with("明细"))).models().isEmpty(), expression);
            }
            scope.commit();
        }
    }
}
