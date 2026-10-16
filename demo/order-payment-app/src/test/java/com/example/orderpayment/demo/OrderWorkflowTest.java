package com.example.orderpayment.demo;

import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.business.OrderPaymentFlow;
import com.example.orderpayment.order.information.OrderInformation;
import com.example.orderpayment.payment.information.PaymentInformation;
import com.example.orderpayment.order.dao.OrderDao;
import com.example.orderpayment.order.dao.OrderDetailDao;
import com.example.orderpayment.order.enums.OrderStatus;
import com.example.orderpayment.order.enums.OrderType;
import com.example.orderpayment.order.store.LocalOrderStore;
import com.example.orderpayment.order.store.LocalOrderStoreFactory;
import com.example.orderpayment.order.workflow.OrderWorkflow;
import com.example.orderpayment.payment.dao.PayDao;
import com.example.orderpayment.payment.dao.PayDetailDao;
import com.example.orderpayment.payment.enums.PayResultCode;
import com.example.orderpayment.user.dao.UserDao;
import com.example.orderpayment.payment.action.SmsSender;
import com.example.orderpayment.business.OrderPaymentListener;
import com.example.orderpayment.payment.action.SmsNotifyAction;
import com.dec.lite.action.ActionListenerContext;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderWorkflowTest {
    @Autowired LocalOrderStoreFactory storeFactory;
    @Autowired OrderWorkflow workflow;
    @Autowired ApplicationContext applicationContext;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean SmsSender smsSender;
    @org.springframework.boot.test.mock.mockito.SpyBean OrderDao orderDao;
    @org.springframework.boot.test.mock.mockito.SpyBean OrderPaymentListener flowListener;
    @org.springframework.boot.test.mock.mockito.SpyBean SmsNotifyAction smsAction;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM dec_demo_payment_error");
        jdbc.update("DELETE FROM dec_demo_pay_detail_info");
        jdbc.update("DELETE FROM dec_demo_pay_info");
        jdbc.update("DELETE FROM dec_demo_order_detail_info");
        jdbc.update("DELETE FROM dec_demo_order_info");
        jdbc.update("DELETE FROM dec_demo_user_info");
    }

    private LocalOrderStore store() {
        return storeFactory.create();
    }

    @Test void generatedDesignProjectionKeepsInformationAndDirectoryNamesTraceable() {
        var sms = OrderPaymentFlow.ERROR.actions().stream()
                .filter(action -> "ACT-SMS-NOTIFY".equals(action.id())).findFirst().orElseThrow();
        assertTrue(sms.isCustom());
        assertEquals("smsNotify", sms.customType());
        assertFalse(OrderPaymentFlow.ORDERED.actions().get(0).isCustom());
        assertEquals("order.ordered", OrderInformation.ORDERED.key());
        assertEquals("order.payable", OrderInformation.PAYABLE.key());
        assertEquals("payment.paymentInfo", PaymentInformation.PAYMENT_INFO.key());
        assertEquals("ordered", OrderPaymentFlow.ORDERED.name());
        assertEquals("paying", OrderPaymentFlow.PAYING.name());
        assertEquals("PayResult", OrderPaymentFlow.PAY_RESULT.name());
        assertEquals(List.of("success", "error"),
                OrderPaymentFlow.PAY_RESULT.edges().stream()
                        .filter(edge -> "case".equals(edge.role()))
                        .map(edge -> edge.rel()).toList());
        assertTrue(OrderPaymentFlow.PAYING.actions().get(0).produces().stream()
                .anyMatch(produce -> "payment.paymentInfo".equals(produce.informationRef())));
        assertTrue(OrderPaymentFlow.PAY_RESULT.actions().get(0).produces().stream()
                .anyMatch(produce -> "payment.hasResult".equals(produce.informationRef())));
        assertTrue(OrderPaymentFlow.ERROR.actions().get(0).produces().stream()
                .anyMatch(produce -> "PyaError".equals(produce.ref()) && produce.informationRef() == null));
    }

    @Test void storeUsesInjectedSystemDaoBeans() {
        LocalOrderStore repository = store();
        assertSame(applicationContext.getBean(OrderDao.class), repository.orders());
        assertSame(applicationContext.getBean(OrderDetailDao.class), repository.orderDetails());
        assertSame(applicationContext.getBean(PayDao.class), repository.pays());
        assertSame(applicationContext.getBean(PayDetailDao.class), repository.payDetails());
        assertSame(applicationContext.getBean(UserDao.class), repository.users());
    }

    @Test void workflowMethodsDoNotOwnTheRequestConcurrencyLock() {
        Arrays.stream(OrderWorkflow.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .forEach(method -> assertFalse(Modifier.isSynchronized(method.getModifiers()),
                        () -> method.getName() + " must delegate concurrency to the transaction/store boundary"));
    }

    @Test void localStoreIsPlainFactoryOwnedObjectWithoutLocks() {
        assertSame(storeFactory.create(), storeFactory.create());
        assertTrue(applicationContext.getBeansOfType(LocalOrderStore.class).isEmpty());
        assertTrue(Arrays.stream(LocalOrderStore.class.getDeclaredFields())
                .noneMatch(field -> java.util.concurrent.locks.Lock.class.isAssignableFrom(field.getType())));
        Arrays.stream(LocalOrderStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .forEach(method -> assertFalse(Modifier.isSynchronized(method.getModifiers()),
                        () -> method.getName() + " must not own a Java monitor lock"));
    }

    @Test void successRunsEveryDirectoryAndKeepsOrderAndPaymentConsistent() {
        var ordered = workflow.submitOrder(request(1), 101L);
        assertEquals(OrderStatus.WAIT_PAY, ordered.getStatus());
        assertEquals(new BigDecimal("29.90"), ordered.getTotalAmount());
        assertEquals("demo-order", ordered.getRemark());
        assertEquals(OrderType.NORMAL, ordered.getType());
        assertEquals(1, ordered.getOrderDetailList().size());
        assertEquals(OrderStatus.WAIT_PAY, ordered.getOrderDetailList().get(0).getStatus());
        assertNull(ordered.getPayInfo());
        assertEquals(OrderStatus.WAIT_PAY, store().orders().find(101L).getOrderStatus());
        assertEquals(OrderStatus.WAIT_PAY, store().orderDetails().find(101L).getStatus());

        var paying = workflow.startPayment(101L);
        assertEquals(OrderStatus.PAYING, paying.getStatus());
        assertEquals(OrderStatus.PAYING, paying.getOrderDetailList().get(0).getStatus());
        assertEquals(101, paying.getPayInfo().getId());
        assertEquals(paying.getTotalAmount(), paying.getPayInfo().getPayDetailList().get(0).getPayAmount());
        assertNotNull(store().pays().find(101L));
        assertNotNull(store().payDetails().find(101L));

        var success = workflow.receivePaymentResult(101L, PayResultCode.SUCCESS);
        assertEquals(OrderStatus.PAY_SUCCESS, success.getStatus());
        assertEquals(OrderStatus.PAY_SUCCESS, success.getOrderDetailList().get(0).getStatus());
        assertEquals(PayResultCode.SUCCESS, success.getPayInfo().getResultCode());
        assertEquals(success.getStatus(), workflow.getOrder(101L).getStatus());
        assertEquals(OrderStatus.PAY_SUCCESS, store().orders().find(101L).getOrderStatus());
        assertEquals(PayResultCode.SUCCESS, store().pays().find(101L).getResultCode());
    }

    @Test void workflowPersistsThroughConfiguredMySqlDataSource() {
        workflow.submitOrder(request(1), 104L);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dec_demo_order_info WHERE o_id = ?", Integer.class, 104));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dec_demo_order_detail_info WHERE od_id = ?", Integer.class, 104));
    }

    @Test void errorSendsLocalNotificationAndBackResetsResultBeforeRetry() {
        workflow.submitOrder(request(1), 102L);
        workflow.startPayment(102L);
        var error = workflow.receivePaymentResult(102L, PayResultCode.ERROR);
        assertEquals(OrderStatus.PAY_ERROR, error.getStatus());
        assertEquals(OrderStatus.PAY_ERROR, error.getOrderDetailList().get(0).getStatus());
        assertEquals(PayResultCode.ERROR, error.getPayInfo().getResultCode());
        assertEquals(1, paymentErrorCount(102));

        var paying = workflow.backToPaying(102L);
        assertEquals(OrderStatus.PAYING, paying.getStatus());
        assertNull(paying.getPayInfo().getResultCode());
        assertEquals(OrderStatus.PAYING, paying.getOrderDetailList().get(0).getStatus());
        var retried = workflow.receivePaymentResult(102L, PayResultCode.SUCCESS);
        assertEquals(OrderStatus.PAY_SUCCESS, retried.getStatus());
        assertEquals(1, paymentErrorCount(102));
    }

    @Test void customSmsActionSendsOnlyAfterTheOuterTransactionCommits() {
        workflow.submitOrder(request(1), 105L);
        workflow.startPayment(105L);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workflow.receivePaymentResult(105L, PayResultCode.ERROR);
            verifyNoInteractions(smsSender);
        });
        verify(smsSender).sendPaymentError(105L);
        verifyNoMoreInteractions(smsSender);
        assertEquals(OrderStatus.PAY_ERROR, workflow.getOrder(105L).getStatus());
    }

    @Test void customSmsActionDoesNotSendWhenTheOuterTransactionRollsBack() {
        workflow.submitOrder(request(1), 106L);
        workflow.startPayment(106L);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workflow.receivePaymentResult(106L, PayResultCode.ERROR);
            verifyNoInteractions(smsSender);
            status.setRollbackOnly();
        });
        verifyNoInteractions(smsSender);
        assertEquals(OrderStatus.PAYING, workflow.getOrder(106L).getStatus());
        assertEquals(0, paymentErrorCount(106));
    }

    @Test void dependencyFailureAndInvalidTransitionsNeverPublishPartialState() {
        workflow.submitOrder(request(2), 103L); // local user 2 is inactive
        assertThrows(ResponseStatusException.class, () -> workflow.startPayment(103L));
        assertEquals(OrderStatus.WAIT_PAY, workflow.getOrder(103L).getStatus());
        assertNull(workflow.getOrder(103L).getPayInfo());
        assertNull(store().pays().find(103L));
        assertNull(store().payDetails().find(103L));
        assertThrows(ResponseStatusException.class, () -> workflow.receivePaymentResult(103L, PayResultCode.SUCCESS));
        assertEquals(OrderStatus.WAIT_PAY, workflow.getOrder(103L).getStatus());
        assertThrows(ResponseStatusException.class, () -> workflow.submitOrder(request(1), 103L));
        assertThrows(ResponseStatusException.class, () -> workflow.getOrder(999L));
        assertEquals(0, paymentErrorCount(103));
    }

    @Test void rejectsRepeatedAndSkippedCommandsBeforeSideEffects() {
        workflow.submitOrder(request(1), 107L);
        assertConflict(() -> workflow.receivePaymentResult(107L, PayResultCode.SUCCESS));
        assertNull(store().pays().find(107L));
        workflow.startPayment(107L);
        assertConflict(() -> workflow.startPayment(107L));
        assertConflict(() -> workflow.backToPaying(107L));
        workflow.receivePaymentResult(107L, PayResultCode.SUCCESS);
        assertConflict(() -> workflow.receivePaymentResult(107L, PayResultCode.ERROR));
        assertEquals(PayResultCode.SUCCESS, workflow.getOrder(107L).getPayInfo().getResultCode());
        assertEquals(0, paymentErrorCount(107));
        verifyNoInteractions(smsSender);
    }

    @Test void canonicalInformationRejectsMismatchedDetailAndPaymentStates() {
        workflow.submitOrder(request(1), 108L);
        jdbc.update("UPDATE dec_demo_order_detail_info SET od_status = 2 WHERE od_id = 108");
        assertConflict(() -> workflow.getOrder(108L));
        assertConflict(() -> workflow.startPayment(108L));
        assertNull(store().pays().find(108L));
        jdbc.update("UPDATE dec_demo_order_detail_info SET od_status = 1 WHERE od_id = 108");
        workflow.startPayment(108L);
        jdbc.update("UPDATE dec_demo_pay_info SET p_result_code = 1 WHERE p_id = 108");
        assertConflict(() -> workflow.receivePaymentResult(108L, PayResultCode.ERROR));
        jdbc.update("UPDATE dec_demo_pay_info SET p_result_code = NULL, p_status = 0 WHERE p_id = 108");
        assertConflict(() -> workflow.receivePaymentResult(108L, PayResultCode.SUCCESS));
        jdbc.update("UPDATE dec_demo_order_info SET o_status = 1 WHERE o_id = 108");
        jdbc.update("UPDATE dec_demo_order_detail_info SET od_status = 1 WHERE od_id = 108");
        assertConflict(() -> workflow.startPayment(108L)); // ordered must not already own a payment
        assertEquals(0, paymentErrorCount(108));
    }

    @Test void directWorkflowCallsReuseGeneratedRequestConstraints() {
        var invalid = request(1);
        invalid.setProductName("x".repeat(121));
        var error = assertThrows(ResponseStatusException.class, () -> workflow.submitOrder(invalid, 109L));
        assertEquals(400, error.getStatusCode().value());
        assertNull(store().orders().find(109L));
        assertNull(store().users().find(1L));
        invalid.setProductName("Demo item");
        invalid.setProductPrice(BigDecimal.ZERO);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> workflow.submitOrder(invalid, 109L)).getStatusCode().value());
    }

    @Test void persistedInformationMismatchRollsBackActionAndFinalWrites() {
        workflow.submitOrder(request(1), 110L);
        doAnswer(invocation -> {
            com.example.orderpayment.order.entity.Order value = invocation.getArgument(0);
            value.setOrderStatus(OrderStatus.WAIT_PAY); // simulate an incorrect persistence mapping
            return invocation.callRealMethod();
        }).when(orderDao).update(any());
        assertConflict(() -> workflow.startPayment(110L));
        assertEquals(OrderStatus.WAIT_PAY, store().orders().find(110L).getOrderStatus());
        assertEquals(OrderStatus.WAIT_PAY, store().orderDetails().find(110L).getStatus());
        assertNull(store().pays().find(110L));
        assertNull(store().payDetails().find(110L));
    }

    @Test void finalWriteFailureRollsBackPaymentActions() {
        workflow.submitOrder(request(1), 111L);
        doThrow(new IllegalStateException("injected final persistence failure")).when(orderDao).update(any());
        assertThrows(IllegalStateException.class, () -> workflow.startPayment(111L));
        assertEquals(OrderStatus.WAIT_PAY, store().orders().find(111L).getOrderStatus());
        assertEquals(OrderStatus.WAIT_PAY, store().orderDetails().find(111L).getStatus());
        assertNull(store().pays().find(111L));
        assertNull(store().payDetails().find(111L));
        verifyNoInteractions(smsSender);
    }

    @Test void concurrentPaymentStartsCreateExactlyOnePayment() throws Exception {
        workflow.submitOrder(request(1), 120L);
        assertEquals(List.of("conflict", "success"), race(() -> workflow.startPayment(120L)));
        assertEquals(OrderStatus.PAYING, workflow.getOrder(120L).getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dec_demo_pay_info WHERE p_order_id=120", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dec_demo_pay_detail_info WHERE pd_pay_id=120", Integer.class));
        verifyNoInteractions(smsSender);
    }

    @Test void duplicateConcurrentCallbacksAndLateConflictingResultDoNotRepeatSideEffects() throws Exception {
        workflow.submitOrder(request(1), 121L);
        assertConflict(() -> workflow.receivePaymentResult(121L, PayResultCode.SUCCESS));
        assertNull(workflow.getOrder(121L).getPayInfo());
        workflow.startPayment(121L);
        assertEquals(List.of("conflict", "success"), race(() -> workflow.receivePaymentResult(121L, PayResultCode.ERROR)));
        assertConflict(() -> workflow.receivePaymentResult(121L, PayResultCode.ERROR));
        assertConflict(() -> workflow.receivePaymentResult(121L, PayResultCode.SUCCESS));
        assertEquals(OrderStatus.PAY_ERROR, workflow.getOrder(121L).getStatus());
        assertEquals(PayResultCode.ERROR, workflow.getOrder(121L).getPayInfo().getResultCode());
        assertEquals(1, paymentErrorCount(121));
        verify(smsSender, times(1)).sendPaymentError(121L);
        verifyNoMoreInteractions(smsSender);
    }

    @Test void multipleDetailsAreLoadedPaidChangedAndRolledBackAsOneOrder() {
        workflow.submitOrder(request(1), 122L);
        workflow.submitOrder(request(1), 123L); // another aggregate must stay untouched
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var extra = store().orderDetails().find(122L);
            extra.setId(12201); extra.setProductId(999); extra.setProductName("second product");
            extra.setProductPrice(new BigDecimal("10.00")); extra.setProductAmount(new BigDecimal("10.00"));
            store().orderDetails().insert(extra);
            var order = store().orders().find(122L);
            order.setProductCount(order.getProductCount() + 1);
            order.setTotalPrice(order.getTotalPrice().add(new BigDecimal("10.00")));
            order.setTotalAmount(order.getTotalAmount().add(new BigDecimal("10.00")));
            store().orders().update(order);
        });
        assertEquals(2, workflow.getOrder(122L).getOrderDetailList().size());
        var paying = workflow.startPayment(122L);
        assertEquals(2, paying.getPayInfo().getPayDetailList().size());
        assertEquals(paying.getTotalAmount(), paying.getPayInfo().getPayDetailList().stream()
                .map(row -> row.getPayAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(List.of(OrderStatus.PAYING, OrderStatus.PAYING),
                paying.getOrderDetailList().stream().map(row -> row.getStatus()).toList());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            workflow.receivePaymentResult(122L, PayResultCode.ERROR);
            tx.setRollbackOnly();
        });
        assertEquals(OrderStatus.PAYING, store().orderDetails().find(12201L).getStatus());
        assertEquals(0, paymentErrorCount(122));
        verifyNoInteractions(smsSender);
        workflow.receivePaymentResult(122L, PayResultCode.ERROR);
        var retry = workflow.backToPaying(122L);
        assertTrue(retry.getOrderDetailList().stream().allMatch(row -> row.getStatus() == OrderStatus.PAYING));
        var success = workflow.receivePaymentResult(122L, PayResultCode.SUCCESS);
        assertTrue(success.getOrderDetailList().stream().allMatch(row -> row.getStatus() == OrderStatus.PAY_SUCCESS));
        assertEquals(OrderStatus.PAY_SUCCESS, store().orderDetails().find(12201L).getStatus());
        assertEquals(OrderStatus.WAIT_PAY, workflow.getOrder(123L).getStatus());
        // A bad second row must be visible to every(...), not hidden by the assembler.
        jdbc.update("UPDATE dec_demo_order_detail_info SET od_status=1 WHERE od_id=12201");
        assertConflict(() -> workflow.getOrder(122L));
    }

    @Test void afterCommitSmsFailureLeavesCommittedStateAndRetryDoesNotResend() {
        workflow.submitOrder(request(1), 124L);
        workflow.startPayment(124L);
        doThrow(new IllegalStateException("SMS provider unavailable")).when(smsSender).sendPaymentError(124L);
        assertThrows(IllegalStateException.class, () -> workflow.receivePaymentResult(124L, PayResultCode.ERROR));
        assertEquals(OrderStatus.PAY_ERROR, workflow.getOrder(124L).getStatus());
        assertEquals(1, paymentErrorCount(124));
        assertConflict(() -> workflow.receivePaymentResult(124L, PayResultCode.ERROR));
        verify(smsSender, times(1)).sendPaymentError(124L);
    }

    @Test void actionListenerRunsAroundSmsActionBeforeCommit() {
        workflow.submitOrder(request(1), 131L);
        workflow.startPayment(131L);
        doAnswer(invocation -> {
            ActionListenerContext context = invocation.getArgument(0);
            assertEquals(131, ((Number) context.read("id")).intValue());
            assertEquals(2, ((Number) context.read("payInfo.resultCode")).intValue());
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            verifyNoInteractions(smsSender);
            invocation.callRealMethod(); return null;
        }).when(flowListener).after(argThat(OrderWorkflowTest::smsEvent), any());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workflow.receivePaymentResult(131L, PayResultCode.ERROR);
            var sequence = inOrder(flowListener, smsAction);
            sequence.verify(flowListener).before(argThat(OrderWorkflowTest::smsEvent));
            sequence.verify(smsAction).execute(any());
            sequence.verify(flowListener).after(argThat(OrderWorkflowTest::smsEvent), any());
            verifyNoInteractions(smsSender);
        });
        verify(smsSender).sendPaymentError(131L);
        assertFlowEvents(List.of("ACT-SAVE-ORDER", "ACT-START-PAY", "ACT-RECEIVE-PAY-RESULT",
                "ACT-RECORD-PAY-ERROR", "ACT-SMS-NOTIFY", "ACT-CONFIRM-PAY-ERROR"));
    }

    @Test void beforeListenerFailureRollsBackAndPreventsAction() {
        assertListenerFailureRollsBack(true, 132L);
        verify(smsAction, never()).execute(any());
        verify(flowListener, never()).after(argThat(OrderWorkflowTest::smsEvent), any());
    }

    @Test void afterListenerFailureRollsBackAndSuppressesScheduledSms() {
        assertListenerFailureRollsBack(false, 133L);
        verify(smsAction).execute(any());
    }

    private void assertListenerFailureRollsBack(boolean before, long orderId) {
        workflow.submitOrder(request(1), orderId);
        workflow.startPayment(orderId);
        org.mockito.stubbing.Answer<Object> failure = invocation -> {
            jdbc.update("UPDATE dec_demo_order_info SET remark = ? WHERE id = ?", "listener update", orderId);
            throw new IllegalStateException("listener failed");
        };
        if (before) doAnswer(failure).when(flowListener).before(argThat(OrderWorkflowTest::smsEvent));
        else doAnswer(failure).when(flowListener).after(argThat(OrderWorkflowTest::smsEvent), any());
        assertConflict(() -> workflow.receivePaymentResult(orderId, PayResultCode.ERROR));
        var persisted = workflow.getOrder(orderId);
        assertEquals(OrderStatus.PAYING, persisted.getStatus());
        assertNull(persisted.getPayInfo().getResultCode());
        assertEquals("demo-order", persisted.getRemark());
        assertEquals(0, paymentErrorCount((int) orderId));
        verifyNoInteractions(smsSender);
    }

    @Test void flowListenerObservesSuccessAndBackActions() {
        workflow.submitOrder(request(1), 134L);
        workflow.startPayment(134L);
        workflow.receivePaymentResult(134L, PayResultCode.SUCCESS);
        workflow.backToPaying(134L);
        assertFlowEvents(List.of("ACT-SAVE-ORDER", "ACT-START-PAY", "ACT-RECEIVE-PAY-RESULT",
                "ACT-CONFIRM-PAY-SUCCESS", "ACT-RESET-PAY-RESULT"));
        verify(flowListener, never()).before(argThat(OrderWorkflowTest::smsEvent));
        verify(flowListener, never()).after(argThat(OrderWorkflowTest::smsEvent), any());
    }

    private static boolean smsEvent(ActionListenerContext context) {
        return context != null && "ACT-SMS-NOTIFY".equals(context.action().id());
    }

    private void assertFlowEvents(List<String> actionIds) {
        var events = mockingDetails(flowListener).getInvocations().stream()
                .filter(invocation -> List.of("before", "after").contains(invocation.getMethod().getName()))
                .map(invocation -> {
                    ActionListenerContext context = (ActionListenerContext) invocation.getArguments()[0];
                    assertEquals(OrderPaymentFlow.ID, context.businessId());
                    return invocation.getMethod().getName() + ":" + context.action().id();
                }).toList();
        assertEquals(actionIds.stream().flatMap(id -> java.util.stream.Stream.of("before:" + id, "after:" + id)).toList(), events);
    }

    private static List<String> race(java.util.concurrent.Callable<?> command) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> task = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                try { command.call(); return "success"; }
                catch (ResponseStatusException failure) {
                    assertEquals(409, failure.getStatusCode().value());
                    return "conflict";
                }
            };
            var first = executor.submit(task); var second = executor.submit(task);
            try { assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            return java.util.stream.Stream.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS)).sorted().toList();
        }
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable command) {
        assertEquals(409, assertThrows(ResponseStatusException.class, command).getStatusCode().value());
    }

    private int paymentErrorCount(int orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dec_demo_payment_error WHERE order_id = ?",
                Integer.class, orderId);
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
