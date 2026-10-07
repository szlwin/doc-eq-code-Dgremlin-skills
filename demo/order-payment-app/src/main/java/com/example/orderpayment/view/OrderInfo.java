package com.example.orderpayment.view;

import java.math.BigDecimal;
import com.example.orderpayment.enums.OrderStatus;
import java.time.LocalDate;
import java.util.List;

public class OrderInfo {
    private Integer id;
    private Integer userId;
    private Integer productCount;
    private BigDecimal totalPrice;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Integer payId;
    private LocalDate dateTime;
    private OrderInfoUser user;
    private List<OrderInfoOrderDetailList> orderDetailList;
    private OrderInfoPayInfo payInfo;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    public Integer getProductCount() {
        return productCount;
    }

    public void setProductCount(Integer productCount) {
        this.productCount = productCount;
    }
    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    public Integer getPayId() {
        return payId;
    }

    public void setPayId(Integer payId) {
        this.payId = payId;
    }
    public LocalDate getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDate dateTime) {
        this.dateTime = dateTime;
    }
    public OrderInfoUser getUser() {
        return user;
    }

    public void setUser(OrderInfoUser user) {
        this.user = user;
    }
    public List<OrderInfoOrderDetailList> getOrderDetailList() {
        return orderDetailList;
    }

    public void setOrderDetailList(List<OrderInfoOrderDetailList> orderDetailList) {
        this.orderDetailList = orderDetailList;
    }
    public OrderInfoPayInfo getPayInfo() {
        return payInfo;
    }

    public void setPayInfo(OrderInfoPayInfo payInfo) {
        this.payInfo = payInfo;
    }
}
