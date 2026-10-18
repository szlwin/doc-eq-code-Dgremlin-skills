package com.example.orderpayment.demo;

import com.dec.lite.query.BusinessQuery;
import com.dec.lite.query.QueryCompilationException;
import com.dec.lite.query.QueryResult;
import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.order.enums.OrderStatus;
import com.example.orderpayment.order.enums.OrderType;
import com.example.orderpayment.order.query.OrderQueryExamples;
import com.example.orderpayment.order.service.OrderQueryService;
import com.example.orderpayment.order.workflow.OrderWorkflow;
import com.example.orderpayment.payment.action.SmsSender;
import com.example.orderpayment.payment.enums.PayResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs the current app's actual XML, compiled RuleViews, workflow writes and MySQL reads. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderQueryTest {
    @Autowired OrderWorkflow workflow;
    @Autowired OrderQueryService queries;
    @Autowired OrderQueryExamples examples;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean SmsSender sms;
    @Autowired MockMvc mvc;

    @BeforeEach void seedWorkflowFacts() {
        for (String table : List.of("payment_error", "pay_detail_info", "pay_info", "order_detail_info", "order_info", "user_info"))
            jdbc.update("DELETE FROM dec_demo_" + table);
        for (long id = 701; id <= 704; id++) {
            var request = new SubmitOrderRequest();
            request.setUserId(1); request.setProductId(7); request.setProductName("Query example");
            request.setProductPrice(new BigDecimal("12.50")); request.setProductCount(2);
            request.setStatus(OrderStatus.WAIT_PAY); request.setType(OrderType.NORMAL); request.setRemark("query-demo");
            workflow.submitOrder(request, id);
        }
        workflow.startPayment(702L);
        workflow.startPayment(703L); workflow.receivePaymentResult(703L, PayResultCode.SUCCESS);
        workflow.startPayment(704L); workflow.receivePaymentResult(704L, PayResultCode.ERROR);
        clearInvocations(sms);
    }

    @Test void articleExamplesUseRealDesignAndDoNotExecuteActions() {
        assertEquals(List.of(703, 704), ids(examples.paymentResults()));
        assertEquals(List.of(701, 702, 703, 704), ids(examples.orderedAndLater()));
        assertEquals(List.of(701, 702, 703), ids(examples.orderedToPaymentSuccess()));
        var joined = examples.orderedToPaymentSuccessWithUsers();
        assertEquals(List.of(701, 702, 703), ids(joined));
        assertEquals("demo-user-1", joined.models().getFirst().read("user.name"));
        assertTrue(queries.explain(BusinessQuery.find("订单").eq("支付结果")).contains("dec_demo_order_info"));
        verifyNoInteractions(sms);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dec_demo_payment_error", Integer.class));
    }

    @Test void generatedOrdersControllerExposesFourBusinessQueriesAndTypedArray() throws Exception {
        mvc.perform(get("/api/orders").param("eq", "支付结果"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(703, 704)))
                .andExpect(jsonPath("$[0].status").value(3))
                .andExpect(jsonPath("$[0].dateTime").isString())
                .andExpect(jsonPath("$[0].payInfo.resultCode").value(1));
        mvc.perform(get("/api/orders").param("start", "已订购"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(701, 702, 703, 704)));
        mvc.perform(get("/api/orders").param("start", "已订购").param("end", "支付成功"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(701, 702, 703)));
        mvc.perform(get("/api/orders").param("start", "已订购").param("end", "支付成功")
                        .param("with", "用户,明细").param("offset", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(703)))
                .andExpect(jsonPath("$[0].user.name").value("demo-user-1"))
                .andExpect(jsonPath("$[0].orderDetailList.length()").value(1));
        mvc.perform(get("/api/orders/701")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(701));
        mvc.perform(get("/api/orders").param("offset", "20"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyNoInteractions(sms);
    }

    @Test void queryApiRejectsInvalidSelectorsAndPaginationAsBadRequests() throws Exception {
        mvc.perform(get("/api/orders").param("eq", "支付结果").param("start", "已订购")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("start", "支付成功").param("end", "已订购")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("eq", "不存在")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("with", "不存在")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("with", "用户,")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("eq", "")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("size", "501")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("size", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("offset", "-1")).andExpect(status().isBadRequest());
        assertEquals(List.of(703, 704), ids(examples.paymentResults()));
        verifyNoInteractions(sms);
    }

    @Test void optionalUsersMultipleDetailsPaginationAndBoundFilters() {
        jdbc.update("INSERT INTO dec_demo_order_detail_info SELECT 800,od_order_id,od_user_id,od_product_id,od_product_name,od_product_price,od_product_amount,od_status FROM dec_demo_order_detail_info WHERE od_id=703");
        var result = queries.find(BusinessQuery.find("订单").with("明细").start("已订购").end("支付成功").page(2, 1));
        assertEquals(List.of(703), ids(result));
        assertEquals(2, ((List<?>) result.models().getFirst().read("orderDetailList")).size());
        assertTrue(queries.find(BusinessQuery.find("订单").with("用户").where("user.name", "' OR 1=1 --")).models().isEmpty());
        jdbc.update("DELETE FROM dec_demo_user_info");
        var missing = examples.orderedToPaymentSuccessWithUsers();
        assertEquals(List.of(701, 702, 703), ids(missing));
        assertNull(missing.models().getFirst().read("user"));
        assertThrows(QueryCompilationException.class, () -> queries.find(BusinessQuery.find("订单").start("支付成功").end("已订购")));
        assertThrows(QueryCompilationException.class, () -> queries.find(BusinessQuery.find("订单").with("不存在的关联")));
    }

    @Test void readsShareSpringTransactionAndCurrentFactsFollowBack() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // A query must observe this connection's uncommitted write, not open an independent transaction.
            jdbc.update("UPDATE dec_demo_pay_info SET p_result_code=NULL WHERE p_order_id=703");
            assertEquals(List.of(704), ids(examples.paymentResults()));
            status.setRollbackOnly();
        });
        assertEquals(List.of(703, 704), ids(examples.paymentResults()));
        workflow.backToPaying(703L);
        assertEquals(List.of(704), ids(examples.paymentResults()));
        assertEquals(List.of(701, 702, 703), ids(examples.orderedToPaymentSuccess()));
        // Same order status alone is insufficient: the canonical Information also checks every detail.
        jdbc.update("UPDATE dec_demo_order_detail_info SET od_status=1 WHERE od_order_id=704");
        assertTrue(examples.paymentResults().models().isEmpty());
        verifyNoInteractions(sms);
    }

    private static List<Integer> ids(QueryResult result) {
        return result.models().stream().map(row -> ((Number) row.read("id")).intValue()).toList();
    }
}
