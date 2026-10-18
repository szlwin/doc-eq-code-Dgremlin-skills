package com.example.orderpayment.order.service.impl;

import com.example.orderpayment.order.dto.ReceivePaymentResultRequest;
import com.example.orderpayment.order.dto.SubmitOrderRequest;
import com.example.orderpayment.order.service.OrdersService;
import com.example.orderpayment.order.service.OrderQueryService;
import com.dec.lite.query.BusinessQuery;
import com.dec.lite.query.QueryCompilationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.orderpayment.order.view.OrderInfo;
import com.example.orderpayment.order.workflow.OrderWorkflow;
import org.springframework.stereotype.Service;

/** Application binding for the order System's orders resource. */
@Service
public final class OrdersServiceImpl implements OrdersService {
    private final OrderWorkflow workflow;
    private final OrderQueryService queries;
    private final ObjectMapper mapper;

    public OrdersServiceImpl(OrderWorkflow workflow, OrderQueryService queries, ObjectMapper mapper) {
        this.workflow = workflow; this.queries = queries; this.mapper = mapper;
    }

    /** DEC API-ORDER-QUERY: transport mapping only; predicates and relations are resolved by DEC. */
    @Override
    public List<OrderInfo> queryOrders(String eq, String start, String end, String with, Integer offset, Integer size) {
        BusinessQuery query;
        try {
            query = BusinessQuery.find("订单");
            if (eq != null) query = query.eq(eq);
            if (start != null) query = query.start(start);
            if (end != null) query = query.end(end);
            if (with != null) for (String relation : with.split(",", -1)) query = query.with(relation.trim());
            query = query.page(offset == null ? query.options().offset() : offset,
                    size == null ? query.options().size() : size);
            queries.plan(query); // Invalid selectors are transport errors; execution failures remain server errors.
        } catch (IllegalArgumentException | QueryCompilationException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
        return queries.find(query).models().stream()
                .map(model -> mapper.convertValue(model.values(), OrderInfo.class)).toList();
    }

    /** DEC API-ORDER-BACK-TO-PAYING. */
    @Override
    public OrderInfo backToPaying(Long orderId) {
        return workflow.backToPaying(orderId);
    }

    /** DEC API-ORDER-GET. */
    @Override
    public OrderInfo getOrder(Long orderId) {
        return workflow.getOrder(orderId);
    }

    /** DEC API-ORDER-RECEIVE-PAYMENT-RESULT. */
    @Override
    public OrderInfo receivePaymentResult(ReceivePaymentResultRequest request, Long orderId) {
        return workflow.receivePaymentResult(orderId, request.getResultCode());
    }

    /** DEC API-ORDER-START-PAYMENT. */
    @Override
    public OrderInfo startPayment(Long orderId) {
        return workflow.startPayment(orderId);
    }

    /** DEC API-ORDER-SUBMIT. */
    @Override
    public OrderInfo submitOrder(SubmitOrderRequest request, Long orderId) {
        return workflow.submitOrder(request, orderId);
    }
}
