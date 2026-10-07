package com.example.orderpayment.service;

import org.springframework.stereotype.Service;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-GET from api-order.yaml; override this seam with an application binding. */
@Service
public class GetOrderService {
    public OrderInfo getOrder(Long orderId) {
        throw new UnsupportedOperationException("DESIGN_GAP API-ORDER-GET: application service is not bound to the DEC runtime");
    }
}
