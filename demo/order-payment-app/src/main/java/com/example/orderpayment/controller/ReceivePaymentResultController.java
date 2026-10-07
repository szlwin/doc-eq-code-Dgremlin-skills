package com.example.orderpayment.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.example.orderpayment.service.ReceivePaymentResultService;
import com.example.orderpayment.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.view.OrderInfo;

/** DEC API-ORDER-RECEIVE-PAYMENT-RESULT from api-order.yaml. */
@RestController
@Validated
public class ReceivePaymentResultController {
    private final ReceivePaymentResultService service;

    public ReceivePaymentResultController(ReceivePaymentResultService service) {
        this.service = service;
    }

    @RequestMapping(path = "/api/orders/{orderId}/payment/result", method = RequestMethod.POST)
    public OrderInfo receivePaymentResult(
            @Valid @RequestBody ReceivePaymentResultRequest request,
            /* DEC API-PARAM-RECEIVEPAYMENTRESULT-ORDER-ID */ @PathVariable("orderId") @NotNull @Min(1) @Max(999999999) Long orderId
    ) {
        return service.receivePaymentResult(request, orderId);
    }
}
