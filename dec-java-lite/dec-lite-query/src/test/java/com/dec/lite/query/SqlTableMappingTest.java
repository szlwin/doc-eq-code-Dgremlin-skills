package com.dec.lite.query;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SqlTableMappingTest {
    @Test void mappingAppliesToBothReadsAndJoinsWithoutChangingValuesOrDesign() throws Exception {
        var plan = BusinessQueryTest.fixture(3).compiler().compile(BusinessQuery.find("订单").eq("支付结果")
                .with("用户").where("user.name", "order_info"));
        var mappings = new HashMap<>(Map.of("order_info", "demo_order", "user_info", "demo_user"));
        var translator = new SqlTranslator(new MySqlDialect(), mappings);
        mappings.put("order_info", "changed_after_construction");
        var sql = translator.candidates(plan, 0, 20);
        assertTrue(sql.sql().contains("FROM `demo_order`"));
        assertTrue(sql.sql().contains("LEFT JOIN `demo_user`"));
        assertTrue(translator.details(plan, List.of(1)).sql().contains("FROM `demo_order`"));
        assertEquals("order_info", plan.rootTable());
        assertTrue(sql.parameters().stream().anyMatch(p -> "order_info".equals(p.value())));
        assertThrows(QueryCompilationException.class, () -> new SqlTranslator(new MySqlDialect(), Map.of("order_info", "orders; DROP TABLE users")));
    }
}
