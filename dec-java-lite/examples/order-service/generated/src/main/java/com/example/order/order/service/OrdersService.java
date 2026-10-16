package com.example.order.order.service;

import com.example.order.order.dto.SubmitOrderRequest;
import com.example.order.order.view.OrderInfo;

/** Application binding seam for API resource orders. */
public interface OrdersService {
    /** DEC API-ORDER-SUBMIT; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT]; XML api-order.xml api[name=submitOrder]. */
    OrderInfo submitOrder(SubmitOrderRequest request, Long orderId);

}
