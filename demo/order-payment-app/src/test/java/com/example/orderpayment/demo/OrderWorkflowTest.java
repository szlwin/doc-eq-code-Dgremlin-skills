package com.example.orderpayment.demo;

import com.example.orderpayment.dto.SubmitOrderRequest;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.OrderType;
import com.example.orderpayment.enums.PayResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderWorkflowTest {
    @Test void successRunsEveryDirectoryAndKeepsOrderAndPaymentConsistent() {
        LocalOrderStore store = new LocalOrderStore();
        OrderWorkflow workflow = new OrderWorkflow(store);
        var ordered = workflow.submitOrder(request(1), 101L);
        assertEquals(OrderStatus.WAIT_PAY, ordered.getStatus());
        assertEquals(new BigDecimal("29.90"), ordered.getTotalAmount());
        assertEquals("demo-order", ordered.getRemark());
        assertEquals(OrderType.NORMAL, ordered.getType());
        assertEquals(1, ordered.getOrderDetailList().size());
        assertEquals(OrderStatus.WAIT_PAY, ordered.getOrderDetailList().get(0).getStatus());
        assertNull(ordered.getPayInfo());
        assertEquals(OrderStatus.WAIT_PAY, store.begin().orders().find(101L).getOrderStatus());
        assertEquals(OrderStatus.WAIT_PAY, store.begin().orderDetails().find(101L).getStatus());

        var paying = workflow.startPayment(101L);
        assertEquals(OrderStatus.PAYING, paying.getStatus());
        assertEquals(OrderStatus.PAYING, paying.getOrderDetailList().get(0).getStatus());
        assertEquals(101, paying.getPayInfo().getId());
        assertEquals(paying.getTotalAmount(), paying.getPayInfo().getPayDetailList().get(0).getPayAmount());
        assertNotNull(store.begin().pays().find(101L));
        assertNotNull(store.begin().payDetails().find(101L));

        var success = workflow.receivePaymentResult(101L, PayResultCode.SUCCESS);
        assertEquals(OrderStatus.PAY_SUCCESS, success.getStatus());
        assertEquals(OrderStatus.PAY_SUCCESS, success.getOrderDetailList().get(0).getStatus());
        assertEquals(PayResultCode.SUCCESS, success.getPayInfo().getResultCode());
        assertEquals(success.getStatus(), workflow.getOrder(101L).getStatus());
        assertEquals(OrderStatus.PAY_SUCCESS, store.begin().orders().find(101L).getOrderStatus());
        assertEquals(PayResultCode.SUCCESS, store.begin().pays().find(101L).getResultCode());
        assertTrue(workflow.smsNotifications().isEmpty());
    }

    @Test void errorSendsLocalNotificationAndBackResetsResultBeforeRetry() {
        OrderWorkflow workflow = new OrderWorkflow(new LocalOrderStore());
        workflow.submitOrder(request(1), 102L);
        workflow.startPayment(102L);
        var error = workflow.receivePaymentResult(102L, PayResultCode.ERROR);
        assertEquals(OrderStatus.PAY_ERROR, error.getStatus());
        assertEquals(OrderStatus.PAY_ERROR, error.getOrderDetailList().get(0).getStatus());
        assertEquals(PayResultCode.ERROR, error.getPayInfo().getResultCode());
        assertEquals(1, workflow.smsNotifications().size());
        assertEquals(1, workflow.paymentErrors().size());
        assertEquals(102, workflow.paymentErrors().get(0).orderId());

        var paying = workflow.backToPaying(102L);
        assertEquals(OrderStatus.PAYING, paying.getStatus());
        assertNull(paying.getPayInfo().getResultCode());
        assertEquals(OrderStatus.PAYING, paying.getOrderDetailList().get(0).getStatus());
        var retried = workflow.receivePaymentResult(102L, PayResultCode.SUCCESS);
        assertEquals(OrderStatus.PAY_SUCCESS, retried.getStatus());
        assertEquals(1, workflow.smsNotifications().size());
        assertEquals(1, workflow.paymentErrors().size());
    }

    @Test void dependencyFailureAndInvalidTransitionsNeverPublishPartialState() {
        LocalOrderStore store = new LocalOrderStore();
        OrderWorkflow workflow = new OrderWorkflow(store);
        workflow.submitOrder(request(2), 103L); // local user 2 is inactive
        assertThrows(ResponseStatusException.class, () -> workflow.startPayment(103L));
        assertEquals(OrderStatus.WAIT_PAY, workflow.getOrder(103L).getStatus());
        assertNull(workflow.getOrder(103L).getPayInfo());
        assertNull(store.begin().pays().find(103L));
        assertNull(store.begin().payDetails().find(103L));
        assertThrows(ResponseStatusException.class, () -> workflow.receivePaymentResult(103L, PayResultCode.SUCCESS));
        assertEquals(OrderStatus.WAIT_PAY, workflow.getOrder(103L).getStatus());
        assertThrows(ResponseStatusException.class, () -> workflow.submitOrder(request(1), 103L));
        assertThrows(ResponseStatusException.class, () -> workflow.getOrder(999L));
        assertTrue(workflow.smsNotifications().isEmpty());
        assertTrue(workflow.paymentErrors().isEmpty());
    }

    private static SubmitOrderRequest request(int userId) {
        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setStatus(OrderStatus.WAIT_PAY);
        request.setType(OrderType.NORMAL);
        request.setRemark("demo-order");
        request.setUserId(userId);
        request.setProductId(7);
        request.setProductName("Demo item");
        request.setProductPrice(new BigDecimal("14.95"));
        request.setProductCount(2);
        return request;
    }
}
