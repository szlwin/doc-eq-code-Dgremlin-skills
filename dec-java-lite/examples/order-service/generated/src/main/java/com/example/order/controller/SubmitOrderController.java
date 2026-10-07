package com.example.order.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.example.order.service.SubmitOrderService;
import com.example.order.dto.SubmitOrderRequest;
import com.example.order.view.OrderInfo;

/** DEC API-ORDER-SUBMIT from api-order.yaml. */
@RestController
@Validated
public class SubmitOrderController {
    private final SubmitOrderService service;

    public SubmitOrderController(SubmitOrderService service) {
        this.service = service;
    }

    @RequestMapping(path = "/api/orders/{orderId}/submit", method = RequestMethod.POST)
    public OrderInfo submitOrder(
            @Valid @RequestBody SubmitOrderRequest request,
            /* DEC API-PARAM-ORDER-ID */ @PathVariable("orderId") @NotNull @Min(1) @Max(999999999) Long orderId
    ) {
        return service.submitOrder(request, orderId);
    }
}
