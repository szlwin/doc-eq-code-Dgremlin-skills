package com.example.orderpayment.service;

import org.springframework.stereotype.Service;
import com.example.orderpayment.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-RECEIVE-PAYMENT-RESULT from api-order.yaml; override this seam with an application binding. */
@Service
public class ReceivePaymentResultService {
    public OrderInfo receivePaymentResult(ReceivePaymentResultRequest request, Long orderId) {
        throw new UnsupportedOperationException("DESIGN_GAP API-ORDER-RECEIVE-PAYMENT-RESULT: application service is not bound to the DEC runtime");
    }
}
