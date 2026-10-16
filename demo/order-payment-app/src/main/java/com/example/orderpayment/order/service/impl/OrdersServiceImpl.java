package com.example.orderpayment.order.service.impl;

import com.example.orderpayment.order.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.order.service.OrdersService;
import com.example.orderpayment.order.view.OrderInfo;
import com.example.orderpayment.order.workflow.OrderWorkflow;
import org.springframework.stereotype.Service;

/** Application binding for the order System's orders resource. */
@Service
public final class OrdersServiceImpl implements OrdersService {
    private final OrderWorkflow workflow;

    public OrdersServiceImpl(OrderWorkflow workflow) { this.workflow = workflow; }

    /** DEC API-ORDER-BACK-TO-PAYING. */
    @Override
    public OrderInfo backToPaying(Long orderId) {
        return workflow.backToPaying(orderId);
    }

    /** DEC API-ORDER-GET. */
    @Override
    public OrderInfo getOrder(Long orderId) {
        return workflow.getOrder(orderId);
    }

    /** DEC API-ORDER-RECEIVE-PAYMENT-RESULT. */
    @Override
    public OrderInfo receivePaymentResult(ReceivePaymentResultRequest request, Long orderId) {
        return workflow.receivePaymentResult(orderId, request.getResultCode());
    }

    /** DEC API-ORDER-START-PAYMENT. */
    @Override
    public OrderInfo startPayment(Long orderId) {
        return workflow.startPayment(orderId);
    }

    /** DEC API-ORDER-SUBMIT. */
    @Override
    public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        return workflow.submitOrder(request, orderId);
    }
}
