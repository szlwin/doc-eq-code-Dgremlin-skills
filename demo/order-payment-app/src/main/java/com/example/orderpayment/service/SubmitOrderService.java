package com.example.orderpayment.service;

import org.springframework.stereotype.Service;
import com.example.orderpayment.dto.SubmitOrderRequest;
import com.example.orderpayment.view.OrderInfo;

@Service
public class SubmitOrderService {
    public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        throw new UnsupportedOperationException("DESIGN_GAP API-ORDER-SUBMIT: application service is not bound to the DEC runtime");
    }
}
