package com.example.orderpayment.demo;

import com.example.orderpayment.entity.Order;
import com.example.orderpayment.entity.OrderDetail;
import com.example.orderpayment.entity.Pay;
import com.example.orderpayment.entity.PayDetail;
import com.example.orderpayment.entity.User;
import com.example.orderpayment.view.OrderInfo;
import com.example.orderpayment.view.OrderInfoOrderDetailList;
import com.example.orderpayment.view.OrderInfoPayInfo;
import com.example.orderpayment.view.OrderInfoPayInfoPayDetailList;
import com.example.orderpayment.view.OrderInfoUser;
import org.springframework.beans.BeanUtils;

import java.util.List;

/** VIEW-ORDER-INFO: assemble the generated View from generated DAO/Entity types. */
final class OrderViewAssembler {
    OrderInfo draft(Order order, OrderDetail detail, User user) {
        return assemble(order, detail, null, null, user);
    }

    OrderInfo read(LocalOrderStore.UnitOfWork unit, Long orderId) {
        Order order = unit.orders().find(orderId);
        if (order == null) return null;
        OrderDetail detail = unit.orderDetails().find(orderId);
        User user = unit.users().find(order.getUserId().longValue());
        Pay pay = unit.pays().find(orderId);
        PayDetail payDetail = unit.payDetails().find(orderId);
        return assemble(order, detail, pay, payDetail, user);
    }

    void persistFinal(LocalOrderStore.UnitOfWork unit, OrderInfo view) {
        Long id = view.getId().longValue();
        Order order = unit.orders().find(id);
        if (order == null) throw new IllegalStateException("DEC ordered action did not insert Order entity: " + id);
        order.setOrderStatus(view.getStatus());
        order.setPayId(view.getPayId());
        unit.orders().update(order);
        OrderDetail detail = unit.orderDetails().find(id);
        if (detail == null) throw new IllegalStateException("DEC ordered action did not insert OrderDetail entity: " + id);
        detail.setStatus(view.getOrderDetailList().get(0).getStatus());
        unit.orderDetails().update(detail);
        if (view.getPayInfo() != null) {
            Pay pay = unit.pays().find(id);
            if (pay == null) throw new IllegalStateException("DEC pay action did not insert Pay entity: " + id);
            pay.setStatus(view.getPayInfo().getStatus());
            pay.setResultCode(view.getPayInfo().getResultCode());
            unit.pays().update(pay);
        }
    }

    Order orderEntity(OrderInfo view) {
        Order order = new Order();
        order.setId(view.getId()); order.setUserId(view.getUserId());
        order.setProductCount(view.getProductCount());
        order.setTotalPrice(view.getTotalPrice()); order.setTotalAmount(view.getTotalAmount());
        order.setOrderStatus(view.getStatus()); order.setPayId(view.getPayId());
        order.setCreateDate(view.getDateTime()); order.setRemark(view.getRemark()); order.setOrderType(view.getType());
        return order;
    }

    OrderDetail detailEntity(OrderInfoOrderDetailList view) {
        OrderDetail detail = new OrderDetail();
        BeanUtils.copyProperties(view, detail);
        return detail;
    }

    Pay payEntity(OrderInfoPayInfo view) {
        Pay pay = new Pay();
        BeanUtils.copyProperties(view, pay);
        return pay;
    }

    PayDetail payDetailEntity(OrderInfoPayInfoPayDetailList view) {
        PayDetail detail = new PayDetail();
        BeanUtils.copyProperties(view, detail);
        return detail;
    }

    private OrderInfo assemble(Order order, OrderDetail detail, Pay pay, PayDetail payDetail, User user) {
        if (detail == null || user == null) throw new IllegalStateException("Order View relation is incomplete");
        OrderInfo view = new OrderInfo();
        view.setId(order.getId()); view.setUserId(order.getUserId());
        view.setProductCount(order.getProductCount()); view.setTotalPrice(order.getTotalPrice());
        view.setTotalAmount(order.getTotalAmount()); view.setStatus(order.getOrderStatus());
        view.setPayId(order.getPayId()); view.setDateTime(order.getCreateDate());
        view.setRemark(order.getRemark()); view.setType(order.getOrderType());
        OrderInfoUser userView = new OrderInfoUser();
        BeanUtils.copyProperties(user, userView);
        view.setUser(userView);
        OrderInfoOrderDetailList line = new OrderInfoOrderDetailList();
        BeanUtils.copyProperties(detail, line);
        view.setOrderDetailList(List.of(line));
        if (pay != null) {
            if (payDetail == null) throw new IllegalStateException("Payment View relation is incomplete");
            OrderInfoPayInfo payment = new OrderInfoPayInfo();
            BeanUtils.copyProperties(pay, payment);
            OrderInfoPayInfoPayDetailList row = new OrderInfoPayInfoPayDetailList();
            BeanUtils.copyProperties(payDetail, row);
            payment.setPayDetailList(List.of(row));
            view.setPayInfo(payment);
        }
        return view;
    }
}
