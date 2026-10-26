package com.example.orderpayment.order.workflow;
import com.dec.lite.action.CompiledRule;
import com.dec.lite.action.DataOperationAdapter;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;
import com.example.orderpayment.order.entity.OrderDetail;
import com.example.orderpayment.order.store.LocalOrderStore;
import com.example.orderpayment.order.view.OrderViewAssembler;
import com.example.orderpayment.order.workflow.DecViewModelBridge;
import com.example.orderpayment.payment.adapter.LocalPaymentErrorAdapter;
import com.example.orderpayment.payment.entity.Pay;
import com.example.orderpayment.payment.enums.PayResultCode;
import com.example.orderpayment.payment.enums.PaymentStatus;
import com.example.orderpayment.order.view.OrderInfo;
import com.example.orderpayment.order.view.OrderInfoPayInfo;
import com.example.orderpayment.order.view.OrderInfoPayInfoPayDetailList;

import java.util.List;

/** DataOperationAdapter for the five external Rules in the canonical Demo. */
final class LocalRuleAdapter implements DataOperationAdapter {
    private final LocalOrderStore store;
    private final DecViewModelBridge bridge;
    private final OrderViewAssembler assembler;
    private final LocalPaymentErrorAdapter paymentErrors;

    LocalRuleAdapter(LocalOrderStore store, DecViewModelBridge bridge,
                     OrderViewAssembler assembler, LocalPaymentErrorAdapter paymentErrors) {
        this.store = store; this.bridge = bridge; this.assembler = assembler; this.paymentErrors = paymentErrors;
    }

    @Override public RecognitionResult execute(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        try {
            return executeRule(rule, invocation);
        } catch (org.springframework.dao.DuplicateKeyException | org.springframework.dao.ConcurrencyFailureException conflict) {
            throw new com.dec.lite.runtime.RuntimeFailure(com.dec.lite.runtime.RuntimeErrorCode.CONFLICT,
                    rule.id(), null, "Data conflict in " + rule.id(), conflict);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw new com.dec.lite.runtime.RuntimeFailure(com.dec.lite.runtime.RuntimeErrorCode.INFRASTRUCTURE,
                    rule.id(), null, "DataSource failure in " + rule.id(), unavailable);
        }
    }

    private RecognitionResult executeRule(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        String expected = switch (rule.id()) {
            case "RULE-INSERT-PAY" -> "paymentService";
            case "RULE-RECORD-PAY-ERROR" -> "mongo1";
            case "RULE-INSERT-ORDER", "RULE-INSERT-DETAIL", "RULE-RECEIVE-PAY-RESULT" -> "data1";
            default -> throw new IllegalStateException("No local adapter for " + rule.id());
        };
        if (!expected.equals(rule.dataSource())) throw new IllegalStateException("DataSource drift for " + rule.id());
        switch (rule.id()) {
            case "RULE-INSERT-ORDER" -> {
                OrderInfo order = bridge.fromModel(invocation.context());
                store.orders().insert(assembler.orderEntity(order));
                invocation.produce("OrderInfo", order);
            }
            case "RULE-INSERT-DETAIL" -> {
                OrderInfo order = bridge.fromModel(invocation.context());
                for (var line : order.getOrderDetailList()) store.orderDetails().insert(assembler.detailEntity(line));
            }
            case "RULE-INSERT-PAY" -> {
                Integer orderId = ((Number) invocation.context().read("id")).intValue();
                var items = store.detailsForOrder(orderId.longValue());
                if (items.isEmpty()) throw new IllegalStateException("OrderDetail is required before payment");
                OrderInfoPayInfo payment = new OrderInfoPayInfo();
                payment.setId(orderId); payment.setOrderId(orderId); payment.setUserId(items.get(0).getUserId());
                payment.setStatus(PaymentStatus.PROCESSING);
                payment.setPayDetailList(items.stream().map(item -> {
                    OrderInfoPayInfoPayDetailList detail = new OrderInfoPayInfoPayDetailList();
                    // Local provider keeps globally unique order-line IDs as payment-line IDs.
                    detail.setId(item.getId()); detail.setPayId(orderId); detail.setProductId(item.getProductId());
                    detail.setProductName(item.getProductName()); detail.setPayPrice(item.getProductPrice());
                    detail.setPayAmount(item.getProductAmount());
                    return detail;
                }).toList());
                store.pays().insert(assembler.payEntity(payment));
                for (var detail : payment.getPayDetailList()) store.payDetails().insert(assembler.payDetailEntity(detail));
                invocation.write("payInfo", bridge.toPaymentModel(payment));
                invocation.produce("PaymentInfo", payment);
            }
            case "RULE-RECEIVE-PAY-RESULT" -> {
                Object raw = invocation.payload().get("resultCode");
                if (!(raw instanceof Number code)) throw new IllegalArgumentException("PayResultCode Integer value is required");
                PayResultCode result = PayResultCode.fromValue(code.intValue());
                Integer orderId = ((Number) invocation.context().read("id")).intValue();
                Pay pay = store.pays().find(orderId.longValue());
                if (pay == null) throw new IllegalStateException("Payment does not exist: " + orderId);
                pay.setResultCode(result);
                store.pays().update(pay);
                invocation.write("payInfo.resultCode", result.getValue());
                invocation.produce("PayResult", result);
            }
            case "RULE-RECORD-PAY-ERROR" -> {
                Integer orderId = ((Number) invocation.context().read("id")).intValue();
                invocation.produce("PayError", paymentErrors.record(orderId, bridge.fromModel(invocation.context()).getPayInfo().getResultCode()));
            }
            default -> throw new IllegalStateException("Unexpected Rule " + rule.id());
        }
        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.id());
    }
}
