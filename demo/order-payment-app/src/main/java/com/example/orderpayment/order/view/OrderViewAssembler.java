package com.example.orderpayment.order.view;
import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.entity.OrderDetail;
import com.example.orderpayment.order.store.LocalOrderStore;
import com.example.orderpayment.payment.entity.Pay;
import com.example.orderpayment.payment.entity.PayDetail;
import com.example.orderpayment.user.entity.User;
import com.example.orderpayment.order.view.OrderInfo;
import com.example.orderpayment.order.view.OrderInfoOrderDetailList;
import com.example.orderpayment.order.view.OrderInfoPayInfo;
import com.example.orderpayment.order.view.OrderInfoPayInfoPayDetailList;
import com.example.orderpayment.order.view.OrderInfoUser;
import org.springframework.beans.BeanUtils;

import java.util.List;

/** VIEW-ORDER-INFO: assemble the generated View from generated DAO/Entity types. */
public final class OrderViewAssembler {
    public OrderInfo draft(Order order, OrderDetail detail, User user) {
        return assemble(order, List.of(detail), null, List.of(), user);
    }

    public OrderInfo read(LocalOrderStore store, Long orderId) {
        Order order = store.orders().find(orderId);
        if (order == null) return null;
        List<OrderDetail> details = store.detailsForOrder(orderId);
        User user = store.users().find(order.getUserId().longValue());
        Pay pay = store.pays().find(orderId);
        List<PayDetail> payDetails = pay == null ? List.of() : store.detailsForPayment(pay.getId().longValue());
        return assemble(order, details, pay, payDetails, user);
    }

    public void persistFinal(LocalOrderStore store, OrderInfo view) {
        Long id = view.getId().longValue();
        Order order = store.orders().find(id);
        if (order == null) throw new IllegalStateException("DEC ordered action did not insert Order entity: " + id);
        order.setOrderStatus(view.getStatus());
        order.setPayId(view.getPayId());
        store.orders().update(order);
        for (var line : view.getOrderDetailList()) {
            OrderDetail detail = store.orderDetails().find(line.getId().longValue());
            if (detail == null || !view.getId().equals(detail.getOrderId()))
                throw new IllegalStateException("OrderDetail does not belong to this order: " + line.getId());
            detail.setStatus(line.getStatus());
            store.orderDetails().update(detail);
        }
        if (view.getPayInfo() != null) {
            Pay pay = store.pays().find(id);
            if (pay == null) throw new IllegalStateException("DEC pay action did not insert Pay entity: " + id);
            pay.setStatus(view.getPayInfo().getStatus());
            pay.setResultCode(view.getPayInfo().getResultCode());
            store.pays().update(pay);
        }
    }

    public Order orderEntity(OrderInfo view) {
        Order order = new Order();
        order.setId(view.getId()); order.setUserId(view.getUserId());
        order.setProductCount(view.getProductCount());
        order.setTotalPrice(view.getTotalPrice()); order.setTotalAmount(view.getTotalAmount());
        order.setOrderStatus(view.getStatus()); order.setPayId(view.getPayId());
        order.setCreateDate(view.getDateTime()); order.setRemark(view.getRemark()); order.setOrderType(view.getType());
        return order;
    }

    public OrderDetail detailEntity(OrderInfoOrderDetailList view) {
        OrderDetail detail = new OrderDetail();
        BeanUtils.copyProperties(view, detail);
        return detail;
    }

    public Pay payEntity(OrderInfoPayInfo view) {
        Pay pay = new Pay();
        BeanUtils.copyProperties(view, pay);
        return pay;
    }

    public PayDetail payDetailEntity(OrderInfoPayInfoPayDetailList view) {
        PayDetail detail = new PayDetail();
        BeanUtils.copyProperties(view, detail);
        return detail;
    }

    private OrderInfo assemble(Order order, List<OrderDetail> details, Pay pay, List<PayDetail> payDetails, User user) {
        if (details.isEmpty() || user == null) throw new IllegalStateException("Order View relation is incomplete");
        OrderInfo view = new OrderInfo();
        view.setId(order.getId()); view.setUserId(order.getUserId());
        view.setProductCount(order.getProductCount()); view.setTotalPrice(order.getTotalPrice());
        view.setTotalAmount(order.getTotalAmount()); view.setStatus(order.getOrderStatus());
        view.setPayId(order.getPayId()); view.setDateTime(order.getCreateDate());
        view.setRemark(order.getRemark()); view.setType(order.getOrderType());
        OrderInfoUser userView = new OrderInfoUser();
        BeanUtils.copyProperties(user, userView);
        view.setUser(userView);
        view.setOrderDetailList(details.stream().map(detail -> {
            OrderInfoOrderDetailList line = new OrderInfoOrderDetailList();
            BeanUtils.copyProperties(detail, line);
            return line;
        }).toList());
        if (pay != null) {
            if (payDetails.isEmpty()) throw new IllegalStateException("Payment View relation is incomplete");
            OrderInfoPayInfo payment = new OrderInfoPayInfo();
            BeanUtils.copyProperties(pay, payment);
            payment.setPayDetailList(payDetails.stream().map(detail -> {
                OrderInfoPayInfoPayDetailList row = new OrderInfoPayInfoPayDetailList();
                BeanUtils.copyProperties(detail, row);
                return row;
            }).toList());
            view.setPayInfo(payment);
        }
        return view;
    }
}
