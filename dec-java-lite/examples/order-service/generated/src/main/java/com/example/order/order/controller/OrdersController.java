package com.example.order.order.controller;

import com.example.order.order.dto.SubmitOrderRequest;
import com.example.order.order.view.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import com.example.order.order.service.OrdersService;

/** API resource orders; each method retains its canonical design references. */
@RestController
@Validated
public class OrdersController {
    private final OrdersService service;

    public OrdersController(OrdersService service) {
        this.service = service;
    }

    /** DEC API-ORDER-SUBMIT; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT]; XML api-order.xml api[name=submitOrder]. */
    @RequestMapping(path = "/api/orders/{orderId}/submit", method = RequestMethod.POST)
    public OrderInfo submitOrder(
            @Valid @RequestBody SubmitOrderRequest request,
            /* DEC API-PARAM-ORDER-ID; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-ORDER-ID]; XML api-order.xml parameter[name=orderId] */ @PathVariable("orderId") @NotNull(message = "订单 ID不能为空") @Min(value = 1, message = "订单 ID最小为1") @Max(value = 999999999, message = "订单 ID最大为999999999") Long orderId
    ) {
        return service.submitOrder(request, orderId);
    }

}
