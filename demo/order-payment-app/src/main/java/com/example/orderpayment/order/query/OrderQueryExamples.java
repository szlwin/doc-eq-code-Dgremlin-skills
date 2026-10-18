package com.example.orderpayment.order.query;

import com.dec.lite.query.BusinessQuery;
import com.dec.lite.query.QueryResult;
import com.example.orderpayment.order.service.OrderQueryService;
import org.springframework.stereotype.Component;

/** Article examples on the real order-payment design; current stages, not past events. */
@Component
public class OrderQueryExamples {
    private final OrderQueryService orders;
    public OrderQueryExamples(OrderQueryService orders) { this.orders = orders; }

    /** DIR-PAY-RESULT: union of its declared success/error cases. */
    public QueryResult paymentResults() {
        return orders.find(BusinessQuery.find("订单").eq("支付结果"));
    }

    /** DIR-ORDERED and its forward-reachable stages. */
    public QueryResult orderedAndLater() {
        return orders.find(BusinessQuery.find("订单").start("已订购"));
    }

    /** DIR-ORDERED through DIR-SUCCESS, including both endpoints. */
    public QueryResult orderedToPaymentSuccess() {
        return orders.find(BusinessQuery.find("订单").start("已订购").end("支付成功"));
    }

    /** VIEW-ORDER-INFO#user uses the design's relation keys and LEFT JOIN. */
    public QueryResult orderedToPaymentSuccessWithUsers() {
        return orders.find(BusinessQuery.find("订单").with("用户").start("已订购").end("支付成功"));
    }
}
