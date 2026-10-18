package com.example.orderpayment.order.controller;

import com.example.orderpayment.order.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.order.view.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.orderpayment.order.service.OrdersService;

/** API resource orders; each method retains its canonical design references. */
@RestController
@Validated
public class OrdersController {
    private final OrdersService service;

    public OrdersController(OrdersService service) {
        this.service = service;
    }

    /** DEC API-ORDER-BACK-TO-PAYING; YAML api-order.yaml#apis[id=API-ORDER-BACK-TO-PAYING]; XML api-order.xml api[name=backToPaying]. */
    @RequestMapping(path = "/api/orders/{orderId}/payment/back", method = RequestMethod.POST)
    public OrderInfo backToPaying(
            /* DEC API-PARAM-BACKTOPAYING-ORDER-ID; YAML api-order.yaml#apis[id=API-ORDER-BACK-TO-PAYING].request.params[id=API-PARAM-BACKTOPAYING-ORDER-ID]; XML api-order.xml parameter[name=orderId] */ @PathVariable("orderId") @NotNull(message = "orderId不能为空") @Min(value = 1, message = "orderId最小为1") @Max(value = 999999999, message = "orderId最大为999999999") Long orderId
    ) {
        return service.backToPaying(orderId);
    }

    /** DEC API-ORDER-GET; YAML api-order.yaml#apis[id=API-ORDER-GET]; XML api-order.xml api[name=getOrder]. */
    @RequestMapping(path = "/api/orders/{orderId}", method = RequestMethod.GET)
    public OrderInfo getOrder(
            /* DEC API-PARAM-GETORDER-ORDER-ID; YAML api-order.yaml#apis[id=API-ORDER-GET].request.params[id=API-PARAM-GETORDER-ORDER-ID]; XML api-order.xml parameter[name=orderId] */ @PathVariable("orderId") @NotNull(message = "orderId不能为空") @Min(value = 1, message = "orderId最小为1") @Max(value = 999999999, message = "orderId最大为999999999") Long orderId
    ) {
        return service.getOrder(orderId);
    }

    /** DEC API-ORDER-QUERY; YAML api-order.yaml#apis[id=API-ORDER-QUERY]; XML api-order.xml api[name=queryOrders]. */
    @RequestMapping(path = "/api/orders", method = RequestMethod.GET)
    public List<OrderInfo> queryOrders(
            /* DEC API-PARAM-QUERY-EQ; YAML api-order.yaml#apis[id=API-ORDER-QUERY].request.params[id=API-PARAM-QUERY-EQ]; XML api-order.xml parameter[name=eq] */ @RequestParam(value = "eq", required = false) @Size(min = 1, message = "eq长度不能小于1") @Size(max = 256, message = "eq长度不能大于256") String eq,
            /* DEC API-PARAM-QUERY-START; YAML api-order.yaml#apis[id=API-ORDER-QUERY].request.params[id=API-PARAM-QUERY-START]; XML api-order.xml parameter[name=start] */ @RequestParam(value = "start", required = false) @Size(min = 1, message = "start长度不能小于1") @Size(max = 256, message = "start长度不能大于256") String start,
            /* DEC API-PARAM-QUERY-END; YAML api-order.yaml#apis[id=API-ORDER-QUERY].request.params[id=API-PARAM-QUERY-END]; XML api-order.xml parameter[name=end] */ @RequestParam(value = "end", required = false) @Size(min = 1, message = "end长度不能小于1") @Size(max = 256, message = "end长度不能大于256") String end,
            /* DEC API-PARAM-QUERY-WITH; YAML api-order.yaml#apis[id=API-ORDER-QUERY].request.params[id=API-PARAM-QUERY-WITH]; XML api-order.xml parameter[name=with] */ @RequestParam(value = "with", required = false) @Size(min = 1, message = "with长度不能小于1") @Size(max = 256, message = "with长度不能大于256") String with,
            /* DEC API-PARAM-QUERY-OFFSET; YAML api-order.yaml#apis[id=API-ORDER-QUERY].request.params[id=API-PARAM-QUERY-OFFSET]; XML api-order.xml parameter[name=offset] */ @RequestParam(value = "offset", required = false) @Min(value = 0, message = "offset不能小于0") @Max(value = 99999, message = "offset不能大于99999") Integer offset,
            /* DEC API-PARAM-QUERY-SIZE; YAML api-order.yaml#apis[id=API-ORDER-QUERY].request.params[id=API-PARAM-QUERY-SIZE]; XML api-order.xml parameter[name=size] */ @RequestParam(value = "size", required = false) @Min(value = 1, message = "size不能小于1") @Max(value = 500, message = "size不能大于500") Integer size
    ) {
        return service.queryOrders(eq, start, end, with, offset, size);
    }

    /** DEC API-ORDER-RECEIVE-PAYMENT-RESULT; YAML api-order.yaml#apis[id=API-ORDER-RECEIVE-PAYMENT-RESULT]; XML api-order.xml api[name=receivePaymentResult]. */
    @RequestMapping(path = "/api/orders/{orderId}/payment/result", method = RequestMethod.POST)
    public OrderInfo receivePaymentResult(
            @Valid @RequestBody ReceivePaymentResultRequest request,
            /* DEC API-PARAM-RECEIVEPAYMENTRESULT-ORDER-ID; YAML api-order.yaml#apis[id=API-ORDER-RECEIVE-PAYMENT-RESULT].request.params[id=API-PARAM-RECEIVEPAYMENTRESULT-ORDER-ID]; XML api-order.xml parameter[name=orderId] */ @PathVariable("orderId") @NotNull(message = "orderId不能为空") @Min(value = 1, message = "orderId最小为1") @Max(value = 999999999, message = "orderId最大为999999999") Long orderId
    ) {
        return service.receivePaymentResult(request, orderId);
    }

    /** DEC API-ORDER-START-PAYMENT; YAML api-order.yaml#apis[id=API-ORDER-START-PAYMENT]; XML api-order.xml api[name=startPayment]. */
    @RequestMapping(path = "/api/orders/{orderId}/payment", method = RequestMethod.POST)
    public OrderInfo startPayment(
            /* DEC API-PARAM-STARTPAYMENT-ORDER-ID; YAML api-order.yaml#apis[id=API-ORDER-START-PAYMENT].request.params[id=API-PARAM-STARTPAYMENT-ORDER-ID]; XML api-order.xml parameter[name=orderId] */ @PathVariable("orderId") @NotNull(message = "orderId不能为空") @Min(value = 1, message = "orderId最小为1") @Max(value = 999999999, message = "orderId最大为999999999") Long orderId
    ) {
        return service.startPayment(orderId);
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
