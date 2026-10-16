package com.example.orderpayment.order.service;

import com.example.orderpayment.order.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.order.view.OrderInfo;

/** Application binding seam for API resource orders. */
public interface OrdersService {
    /** DEC API-ORDER-BACK-TO-PAYING; YAML api-order.yaml#apis[id=API-ORDER-BACK-TO-PAYING]; XML api-order.xml api[name=backToPaying]. */
    OrderInfo backToPaying(Long orderId);

    /** DEC API-ORDER-GET; YAML api-order.yaml#apis[id=API-ORDER-GET]; XML api-order.xml api[name=getOrder]. */
    OrderInfo getOrder(Long orderId);

    /** DEC API-ORDER-RECEIVE-PAYMENT-RESULT; YAML api-order.yaml#apis[id=API-ORDER-RECEIVE-PAYMENT-RESULT]; XML api-order.xml api[name=receivePaymentResult]. */
    OrderInfo receivePaymentResult(ReceivePaymentResultRequest request, Long orderId);

    /** DEC API-ORDER-START-PAYMENT; YAML api-order.yaml#apis[id=API-ORDER-START-PAYMENT]; XML api-order.xml api[name=startPayment]. */
    OrderInfo startPayment(Long orderId);

    /** DEC API-ORDER-SUBMIT; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT]; XML api-order.xml api[name=submitOrder]. */
    OrderInfo submitOrder(SubmitOrderRequest request, Long orderId);

}
