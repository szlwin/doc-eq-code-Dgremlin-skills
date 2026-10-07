package com.example.orderpayment.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.*;
import com.example.orderpayment.service.BackToPayingService;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-BACK-TO-PAYING from api-order.yaml. */
@RestController
@Validated
public class BackToPayingController {
    private final BackToPayingService service;

    public BackToPayingController(BackToPayingService service) {
        this.service = service;
    }

    @RequestMapping(path = "/api/orders/{orderId}/payment/back", method = RequestMethod.POST)
    public OrderInfo backToPaying(
            /* DEC API-PARAM-BACKTOPAYING-ORDER-ID */ @PathVariable("orderId") @NotNull @Min(1) @Max(999999999) Long orderId
    ) {
        return service.backToPaying(orderId);
    }
}
