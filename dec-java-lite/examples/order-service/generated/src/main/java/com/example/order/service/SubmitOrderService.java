package com.example.order.service;

import org.springframework.stereotype.Service;
import com.example.order.dto.SubmitOrderRequest;
import com.example.order.view.OrderInfo;

@Service
public class SubmitOrderService {
    public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        throw new UnsupportedOperationException("DESIGN_GAP API-ORDER-SUBMIT: application service is not bound to the DEC runtime");
    }
}
