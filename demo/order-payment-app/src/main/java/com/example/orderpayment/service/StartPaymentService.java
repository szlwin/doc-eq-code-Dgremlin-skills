package com.example.orderpayment.service;

import org.springframework.stereotype.Service;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-START-PAYMENT from api-order.yaml; override this seam with an application binding. */
@Service
public class StartPaymentService {
    public OrderInfo startPayment(Long orderId) {
        throw new UnsupportedOperationException("DESIGN_GAP API-ORDER-START-PAYMENT: application service is not bound to the DEC runtime");
    }
}
