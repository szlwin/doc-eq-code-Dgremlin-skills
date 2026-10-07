package com.example.orderpayment.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.*;
import com.example.orderpayment.service.StartPaymentService;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-START-PAYMENT from api-order.yaml. */
@RestController
@Validated
public class StartPaymentController {
    private final StartPaymentService service;

    public StartPaymentController(StartPaymentService service) {
        this.service = service;
    }

    @RequestMapping(path = "/api/orders/{orderId}/payment", method = RequestMethod.POST)
    public OrderInfo startPayment(
            /* DEC API-PARAM-STARTPAYMENT-ORDER-ID */ @PathVariable("orderId") @NotNull @Min(1) @Max(999999999) Long orderId
    ) {
        return service.startPayment(orderId);
    }
}
