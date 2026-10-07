package com.example.orderpayment.demo;

import com.example.orderpayment.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.dto.SubmitOrderRequest;
import com.example.orderpayment.service.BackToPayingService;
import com.example.orderpayment.service.GetOrderService;
import com.example.orderpayment.service.ReceivePaymentResultService;
import com.example.orderpayment.service.StartPaymentService;
import com.example.orderpayment.service.SubmitOrderService;
import com.example.orderpayment.view.OrderInfo;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Application implementations override the generated DESIGN_GAP service seams. */
final class OrderApiBindings { private OrderApiBindings() { } }

@Service @Primary
class SubmitOrderBinding extends SubmitOrderService {
    private final OrderWorkflow workflow;
    SubmitOrderBinding(OrderWorkflow workflow) { this.workflow = workflow; }
    @Override public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        return workflow.submitOrder(request, orderId);
    }
}

@Service @Primary
class StartPaymentBinding extends StartPaymentService {
    private final OrderWorkflow workflow;
    StartPaymentBinding(OrderWorkflow workflow) { this.workflow = workflow; }
    @Override public OrderInfo startPayment(Long orderId) { return workflow.startPayment(orderId); }
}

@Service @Primary
class ReceivePaymentResultBinding extends ReceivePaymentResultService {
    private final OrderWorkflow workflow;
    ReceivePaymentResultBinding(OrderWorkflow workflow) { this.workflow = workflow; }
    @Override public OrderInfo receivePaymentResult(ReceivePaymentResultRequest request, Long orderId) {
        return workflow.receivePaymentResult(orderId, request.getResultCode());
    }
}

@Service @Primary
class BackToPayingBinding extends BackToPayingService {
    private final OrderWorkflow workflow;
    BackToPayingBinding(OrderWorkflow workflow) { this.workflow = workflow; }
    @Override public OrderInfo backToPaying(Long orderId) { return workflow.backToPaying(orderId); }
}

@Service @Primary
class GetOrderBinding extends GetOrderService {
    private final OrderWorkflow workflow;
    GetOrderBinding(OrderWorkflow workflow) { this.workflow = workflow; }
    @Override public OrderInfo getOrder(Long orderId) { return workflow.getOrder(orderId); }
}
