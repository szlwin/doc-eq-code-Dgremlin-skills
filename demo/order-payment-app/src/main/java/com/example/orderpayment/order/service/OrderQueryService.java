package com.example.orderpayment.order.service;

import com.dec.lite.parser.DecXmlParser;
import com.dec.lite.query.BusinessQuery;
import com.dec.lite.query.BusinessQueryNames;
import com.dec.lite.query.BusinessQueryRuntime;
import com.dec.lite.query.JdbcQueryExecutor;
import com.dec.lite.query.MySqlDialect;
import com.dec.lite.query.QueryPlan;
import com.dec.lite.query.QueryResult;
import com.dec.lite.query.SqlTranslator;
import com.example.orderpayment.business.OrderPaymentFlow;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.Map;

/**
 * BUS-ORDER-PAYMENT / VIEW-ORDER-INFO: read the existing workflow's persisted facts.
 * Design: business-order-payment.yaml#business.directories; view-order.yaml#views.
 * Derived XML: dec/design/business-order-payment.xml; dec/design/view-order.xml.
 * Application binding; not owned by the generated Controller/Service manifest.
 */
@Service
public class OrderQueryService {
    private final BusinessQueryRuntime runtime;
    private final JdbcQueryExecutor executor;
    private final SqlTranslator translator = new SqlTranslator(new MySqlDialect(), Map.of(
            "order_info", "dec_demo_order_info", "order_detail_info", "dec_demo_order_detail_info",
            "user_info", "dec_demo_user_info", "pay_info", "dec_demo_pay_info",
            "pay_detail_info", "dec_demo_pay_detail_info"));

    public OrderQueryService(DataSource dataSource) {
        var names = new BusinessQueryNames(Map.of("订单", "VIEW-ORDER-INFO"), Map.of(
                "已订购", OrderPaymentFlow.ORDERED.id(), "支付中", OrderPaymentFlow.PAYING.id(),
                "支付结果", OrderPaymentFlow.PAY_RESULT.id(), "支付成功", OrderPaymentFlow.SUCCESS.id(),
                "支付失败", OrderPaymentFlow.ERROR.id()),
                Map.of("OrderInfo", Map.of("用户", "user", "明细", "orderDetailList")));
        runtime = new BusinessQueryRuntime(new DecXmlParser().parseClasspath(
                OrderQueryService.class.getClassLoader(), "dec/design-files.txt"), names);
        var transactionalSource = new TransactionAwareDataSourceProxy(dataSource);
        executor = new JdbcQueryExecutor(route -> {
            if (!route.connection().equals("con1") || !route.dataSource().equals("data1"))
                throw new IllegalArgumentException("No query DataSource registered for " + route);
            if (!TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("Order query requires the Spring transaction proxy");
            // close() releases the proxy handle; Spring owns the physical connection and transaction.
            return transactionalSource.getConnection();
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QueryResult find(BusinessQuery query) {
        // These declared View relations are inputs to canonical Information/RuleView recognition.
        // The caller's with(用户) additionally selects the optional user relation.
        return runtime.run(recognitionInputs(query), executor, translator, null);
    }

    public QueryPlan plan(BusinessQuery query) { return runtime.plan(recognitionInputs(query)); }

    public String explain(BusinessQuery query) { return translator.explain(plan(query)); }

    private static BusinessQuery recognitionInputs(BusinessQuery query) {
        return query.with("payInfo").with("orderDetailList");
    }
}
