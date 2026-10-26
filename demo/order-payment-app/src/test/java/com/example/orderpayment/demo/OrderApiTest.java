package com.example.orderpayment.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.example.orderpayment.order.dao.OrderDao orderDao;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.example.orderpayment.payment.dao.PayDao payDao;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.example.orderpayment.payment.dao.PayDetailDao payDetailDao;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM dec_demo_payment_error");
        jdbc.update("DELETE FROM dec_demo_pay_detail_info");
        jdbc.update("DELETE FROM dec_demo_pay_info");
        jdbc.update("DELETE FROM dec_demo_order_detail_info");
        jdbc.update("DELETE FROM dec_demo_order_info");
        jdbc.update("DELETE FROM dec_demo_user_info");
    }

    @Test void generatedControllersReachTheExecutableWorkflow() throws Exception {
        mvc.perform(post("/api/orders/501/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":1,"type":1,"userId":1,"productId":7,
                         "productName":"Demo item","productPrice":14.95,"productCount":2,"remark":"demo-order"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.remark").value("demo-order"))
                .andExpect(jsonPath("$.type").value(1));
        mvc.perform(post("/api/orders/501/payment"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(2));
        mvc.perform(post("/api/orders/501/payment/result").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultCode\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(4));
        mvc.perform(get("/api/orders/501"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payInfo.resultCode").value(2));
        mvc.perform(post("/api/orders/501/payment/back"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(2))
                .andExpect(jsonPath("$.payInfo.resultCode").doesNotExist());
    }

    @Test void concurrentHttpSubmissionsReturnOneSuccessAndOneConflictWithoutPartialWrites() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            clearDatabase();
            if (scenario == 1)
                jdbc.update("INSERT INTO dec_demo_user_info VALUES (1, 'existing', 1, 1)");
            long orderId = 551L + scenario;
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(call -> {
                Object result = call.callRealMethod();
                if (reads.incrementAndGet() <= 2) {
                    org.junit.jupiter.api.Assertions.assertNull(result, "both requests must pass the initial existence check");
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                }
                return result;
            }).when(orderDao).find(orderId);
            // New shared user, existing shared user, then different new users.
            int secondUser = scenario == 2 ? 3 : 1;
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> submitStatus(orderId, 1));
                var second = executor.submit(() -> submitStatus(orderId, secondUser));
                var statuses = new java.util.ArrayList<>(java.util.List.of(
                        first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                        second.get(20, java.util.concurrent.TimeUnit.SECONDS)));
                statuses.sort(Integer::compareTo);
                org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 409), statuses);
            }
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dec_demo_order_info WHERE o_id=?", Integer.class, orderId));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dec_demo_order_detail_info WHERE od_order_id=?", Integer.class, orderId));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dec_demo_user_info", Integer.class), "losing user initialization must roll back");
        }
    }

    private int submitStatus(long orderId, int userId) throws Exception {
        return mvc.perform(post("/api/orders/{orderId}/submit", orderId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":1,"type":1,"userId":%d,"productId":7,
                         "productName":"Demo item","productPrice":14.95,"productCount":2}
                        """.formatted(userId))).andReturn().getResponse().getStatus();
    }

    @Test void paymentConnectionFailureIsServiceUnavailableAndPreservesCause() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(200, submitStatus(581L, 1));
        var unavailable = new org.springframework.dao.DataAccessResourceFailureException("payment database unavailable");
        org.mockito.Mockito.doThrow(unavailable).when(payDao).insert(org.mockito.ArgumentMatchers.any());
        var result = mvc.perform(post("/api/orders/581/payment"))
                .andExpect(status().isServiceUnavailable()).andReturn();
        Throwable cause = result.getResolvedException();
        while (cause != null && cause != unavailable) cause = cause.getCause();
        org.junit.jupiter.api.Assertions.assertSame(unavailable, cause);
        assertPaymentRolledBack(581L);
    }

    @Test void failureAfterPaymentWritesRollsBackBothPaymentRowsAndOrderState() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(200, submitStatus(582L, 1));
        org.mockito.Mockito.doAnswer(call -> {
            call.callRealMethod();
            throw new org.springframework.dao.DataAccessResourceFailureException("connection failed after detail write");
        }).when(payDetailDao).insert(org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/orders/582/payment")).andExpect(status().isServiceUnavailable());
        assertPaymentRolledBack(582L);
    }

    private void assertPaymentRolledBack(long id) throws Exception {
        mvc.perform(get("/api/orders/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT od_status FROM dec_demo_order_detail_info WHERE od_order_id=?", Integer.class, id));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dec_demo_pay_info", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dec_demo_pay_detail_info", Integer.class));
    }

    @Test void rejectsInvalidTransportInputBeforeCreatingOrder() throws Exception {
        mvc.perform(post("/api/orders/502/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":2,\"type\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/502")).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/503/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":1,"type":1,"userId":1,"productId":7,
                         "productName":"Demo item","productPrice":0.50,"productCount":2}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/503")).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/0/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":1,"type":1,"userId":1,"productId":7,
                         "productName":"Demo item","productPrice":14.95,"productCount":2}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test void evaluatesOrderStatusAndTypeInBusinessRuleView() throws Exception {
        mvc.perform(post("/api/orders/504/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":2,"type":1,"userId":1,"productId":7,
                         "productName":"Demo item","productPrice":14.95,"productCount":2}
                        """))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/orders/504")).andExpect(status().isNotFound());
    }
}
