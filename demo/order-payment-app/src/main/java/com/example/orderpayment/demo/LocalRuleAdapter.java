package com.example.orderpayment.demo;

import com.dec.lite.action.CompiledRule;
import com.dec.lite.action.DataOperationAdapter;
import com.dec.lite.information.RecognitionResult;
import com.dec.lite.information.RuleViewRegistry;
import com.example.orderpayment.entity.OrderDetail;
import com.example.orderpayment.entity.Pay;
import com.example.orderpayment.enums.PayResultCode;
import com.example.orderpayment.enums.PaymentStatus;
import com.example.orderpayment.view.OrderInfo;
import com.example.orderpayment.view.OrderInfoPayInfo;
import com.example.orderpayment.view.OrderInfoPayInfoPayDetailList;

import java.util.List;

/** DataOperationAdapter for the five external Rules in the canonical Demo. */
final class LocalRuleAdapter implements DataOperationAdapter {
    private final LocalOrderStore.UnitOfWork unit;
    private final DecViewModelBridge bridge;
    private final OrderViewAssembler assembler;

    LocalRuleAdapter(LocalOrderStore.UnitOfWork unit, DecViewModelBridge bridge, OrderViewAssembler assembler) {
        this.unit = unit; this.bridge = bridge; this.assembler = assembler;
    }

    @Override public RecognitionResult execute(CompiledRule rule, RuleViewRegistry.Invocation invocation) {
        String expected = switch (rule.id()) {
            case "RULE-INSERT-PAY" -> "paymentService";
            case "RULE-RECORD-PAY-ERROR" -> "mongo1";
            case "RULE-INSERT-ORDER", "RULE-INSERT-DETAIL", "RULE-RECEIVE-PAY-RESULT" -> "data1";
            default -> throw new IllegalStateException("No local adapter for " + rule.id());
        };
        if (!expected.equals(rule.dataSource())) throw new IllegalStateException("DataSource drift for " + rule.id());
        switch (rule.id()) {
            case "RULE-INSERT-ORDER" -> {
                invocation.write("status", 1L);
                OrderInfo order = bridge.fromModel(invocation.context());
                unit.orders().insert(assembler.orderEntity(order));
                invocation.produce("OrderInfo", order);
            }
            case "RULE-INSERT-DETAIL" -> {
                invocation.write("orderDetailList[0].status", 1L);
                OrderInfo order = bridge.fromModel(invocation.context());
                unit.orderDetails().insert(assembler.detailEntity(order.getOrderDetailList().get(0)));
            }
            case "RULE-INSERT-PAY" -> {
                Integer orderId = ((Number) invocation.context().read("id")).intValue();
                OrderDetail item = unit.orderDetails().find(orderId.longValue());
                if (item == null) throw new IllegalStateException("OrderDetail is required before payment");
                OrderInfoPayInfo payment = new OrderInfoPayInfo();
                payment.setId(orderId); payment.setOrderId(orderId); payment.setUserId(item.getUserId());
                payment.setStatus(PaymentStatus.PROCESSING);
                OrderInfoPayInfoPayDetailList detail = new OrderInfoPayInfoPayDetailList();
                detail.setId(orderId); detail.setPayId(orderId); detail.setProductId(item.getProductId());
                detail.setProductName(item.getProductName()); detail.setPayPrice(item.getProductPrice());
                detail.setPayAmount(item.getProductAmount()); payment.setPayDetailList(List.of(detail));
                unit.pays().insert(assembler.payEntity(payment));
                unit.payDetails().insert(assembler.payDetailEntity(detail));
                invocation.write("payInfo", bridge.toPaymentModel(payment));
                invocation.produce("PaymentInfo", payment);
            }
            case "RULE-RECEIVE-PAY-RESULT" -> {
                Object raw = invocation.payload().get("resultCode");
                if (!(raw instanceof String code)) throw new IllegalArgumentException("PayResultCode is required");
                PayResultCode result = PayResultCode.fromValue(code);
                Integer orderId = ((Number) invocation.context().read("id")).intValue();
                Pay pay = unit.pays().find(orderId.longValue());
                if (pay == null) throw new IllegalStateException("Payment does not exist: " + orderId);
                pay.setResultCode(result);
                unit.pays().update(pay);
                invocation.write("payInfo.resultCode", result.getValue());
                invocation.produce("PayResult", result);
            }
            case "RULE-RECORD-PAY-ERROR" -> {
                Integer orderId = ((Number) invocation.context().read("id")).intValue();
                unit.recordError(orderId, PayResultCode.ERROR);
                invocation.produce("PyaError", new LocalOrderStore.PaymentError(orderId, PayResultCode.ERROR));
            }
            default -> throw new IllegalStateException("Unexpected Rule " + rule.id());
        }
        return RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), rule.id());
    }
}
