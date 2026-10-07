package com.example.orderpayment.service;

import org.springframework.stereotype.Service;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-BACK-TO-PAYING from api-order.yaml; override this seam with an application binding. */
@Service
public class BackToPayingService {
    public OrderInfo backToPaying(Long orderId) {
        throw new UnsupportedOperationException("DESIGN_GAP API-ORDER-BACK-TO-PAYING: application service is not bound to the DEC runtime");
    }
}
