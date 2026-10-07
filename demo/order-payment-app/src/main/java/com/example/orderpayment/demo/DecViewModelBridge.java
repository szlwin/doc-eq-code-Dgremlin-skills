package com.example.orderpayment.demo;

import com.dec.lite.information.ModelContext;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.OrderType;
import com.example.orderpayment.enums.PayResultCode;
import com.example.orderpayment.enums.PaymentStatus;
import com.example.orderpayment.view.OrderInfo;
import com.example.orderpayment.view.OrderInfoOrderDetailList;
import com.example.orderpayment.view.OrderInfoPayInfo;
import com.example.orderpayment.view.OrderInfoPayInfoPayDetailList;
import com.example.orderpayment.view.OrderInfoUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The sole dynamic-map boundary required by DEC ModelContext. Application state remains typed. */
final class DecViewModelBridge {
    ModelContext toModel(OrderInfo view) { return new ModelContext(toMap(view)); }

    Map<String, Object> toPaymentModel(OrderInfoPayInfo payment) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", payment.getId()); values.put("orderId", payment.getOrderId());
        values.put("userId", payment.getUserId());
        values.put("status", Long.valueOf(payment.getStatus().getValue()));
        values.put("resultCode", payment.getResultCode() == null ? null : payment.getResultCode().getValue());
        List<Map<String, Object>> details = new ArrayList<>();
        for (OrderInfoPayInfoPayDetailList detail : payment.getPayDetailList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", detail.getId()); row.put("payId", detail.getPayId());
            row.put("productId", detail.getProductId()); row.put("productName", detail.getProductName());
            row.put("payPrice", detail.getPayPrice()); row.put("payAmount", detail.getPayAmount());
            details.add(row);
        }
        values.put("payDetailList", details);
        return values;
    }

    OrderInfo fromModel(ModelContext context) {
        Map<String, Object> model = context.values();
        OrderInfo view = new OrderInfo();
        view.setId(number(model.get("id"))); view.setUserId(number(model.get("userId")));
        view.setProductCount(number(model.get("productCount")));
        view.setTotalPrice((BigDecimal) model.get("totalPrice")); view.setTotalAmount((BigDecimal) model.get("totalAmount"));
        view.setStatus(orderStatus(model.get("status")));
        view.setRemark((String) model.get("remark"));
        view.setType(OrderType.fromValue(String.valueOf(model.get("type"))));
        view.setDateTime((LocalDate) model.get("dateTime"));
        Map<?, ?> user = (Map<?, ?>) model.get("user");
        OrderInfoUser userView = new OrderInfoUser();
        userView.setId(number(user.get("id"))); userView.setName((String) user.get("name"));
        userView.setActive(number(user.get("active"))); userView.setCertified(number(user.get("certified")));
        view.setUser(userView);
        List<OrderInfoOrderDetailList> items = new ArrayList<>();
        for (Object itemValue : (List<?>) model.get("orderDetailList")) {
            Map<?, ?> item = (Map<?, ?>) itemValue;
            OrderInfoOrderDetailList line = new OrderInfoOrderDetailList();
            line.setId(number(item.get("id"))); line.setOrderId(number(item.get("orderId")));
            line.setUserId(number(item.get("userId"))); line.setProductId(number(item.get("productId")));
            line.setProductName((String) item.get("productName"));
            line.setProductPrice((BigDecimal) item.get("productPrice"));
            line.setProductAmount((BigDecimal) item.get("productAmount"));
            line.setStatus(orderStatus(item.get("status")));
            items.add(line);
        }
        view.setOrderDetailList(items);
        Map<?, ?> pay = (Map<?, ?>) model.get("payInfo");
        if (!pay.isEmpty()) {
            OrderInfoPayInfo payment = new OrderInfoPayInfo();
            payment.setId(number(pay.get("id"))); payment.setOrderId(number(pay.get("orderId")));
            payment.setUserId(number(pay.get("userId")));
            payment.setStatus(PaymentStatus.fromValue(String.valueOf(pay.get("status"))));
            if (pay.get("resultCode") != null) payment.setResultCode(PayResultCode.fromValue(String.valueOf(pay.get("resultCode"))));
            view.setPayId(payment.getId());
            List<OrderInfoPayInfoPayDetailList> details = new ArrayList<>();
            for (Object detailValue : (List<?>) pay.get("payDetailList")) {
                Map<?, ?> detail = (Map<?, ?>) detailValue;
                OrderInfoPayInfoPayDetailList row = new OrderInfoPayInfoPayDetailList();
                row.setId(number(detail.get("id"))); row.setPayId(number(detail.get("payId")));
                row.setProductId(number(detail.get("productId"))); row.setProductName((String) detail.get("productName"));
                row.setPayPrice((BigDecimal) detail.get("payPrice")); row.setPayAmount((BigDecimal) detail.get("payAmount"));
                details.add(row);
            }
            payment.setPayDetailList(details); view.setPayInfo(payment);
        }
        return view;
    }

    private Map<String, Object> toMap(OrderInfo view) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", view.getId()); model.put("userId", view.getUserId());
        model.put("productCount", view.getProductCount()); model.put("totalPrice", view.getTotalPrice());
        model.put("totalAmount", view.getTotalAmount());
        model.put("status", view.getStatus() == null ? 0L : Long.valueOf(view.getStatus().getValue()));
        model.put("remark", view.getRemark()); model.put("type", view.getType().getValue());
        model.put("dateTime", view.getDateTime()); model.put("payId", view.getPayId());
        OrderInfoUser user = view.getUser();
        model.put("user", Map.of("id", user.getId(), "name", user.getName(),
                "active", user.getActive().longValue(), "certified", user.getCertified().longValue()));
        model.put("active", user.getActive().longValue()); model.put("certified", user.getCertified().longValue());
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderInfoOrderDetailList line : view.getOrderDetailList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", line.getId()); item.put("orderId", line.getOrderId());
            item.put("userId", line.getUserId()); item.put("productId", line.getProductId());
            item.put("productName", line.getProductName()); item.put("productPrice", line.getProductPrice());
            item.put("productAmount", line.getProductAmount());
            item.put("status", line.getStatus() == null ? 0L : Long.valueOf(line.getStatus().getValue()));
            items.add(item);
        }
        model.put("orderDetailList", items);
        model.put("payInfo", view.getPayInfo() == null ? new LinkedHashMap<>() : toPaymentModel(view.getPayInfo()));
        return model;
    }

    private static Integer number(Object value) { return value == null ? null : ((Number) value).intValue(); }

    // DEC uses 0 only while an ordered action is still materializing its View.
    private static OrderStatus orderStatus(Object value) {
        if (value == null || "0".equals(String.valueOf(value))) return null;
        return OrderStatus.fromValue(String.valueOf(value));
    }
}
