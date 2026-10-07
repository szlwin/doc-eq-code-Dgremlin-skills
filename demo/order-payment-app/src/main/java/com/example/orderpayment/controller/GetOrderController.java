package com.example.orderpayment.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.*;
import com.example.orderpayment.service.GetOrderService;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-GET from api-order.yaml. */
@RestController
@Validated
public class GetOrderController {
    private final GetOrderService service;

    public GetOrderController(GetOrderService service) {
        this.service = service;
    }

    @RequestMapping(path = "/api/orders/{orderId}", method = RequestMethod.GET)
    public OrderInfo getOrder(
            /* DEC API-PARAM-GETORDER-ORDER-ID */ @PathVariable("orderId") @NotNull @Min(1) @Max(999999999) Long orderId
    ) {
        return service.getOrder(orderId);
    }
}
