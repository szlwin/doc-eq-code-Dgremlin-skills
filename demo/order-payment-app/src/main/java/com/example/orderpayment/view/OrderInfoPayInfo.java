package com.example.orderpayment.view;

import com.example.orderpayment.enums.PaymentStatus;
import com.example.orderpayment.enums.PayResultCode;
import java.util.List;

public class OrderInfoPayInfo {
    private Integer id;
    private Integer orderId;
    private Integer userId;
    private PaymentStatus status;
    private PayResultCode resultCode;
    private List<OrderInfoPayInfoPayDetailList> payDetailList;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
    public PayResultCode getResultCode() {
        return resultCode;
    }

    public void setResultCode(PayResultCode resultCode) {
        this.resultCode = resultCode;
    }
    public List<OrderInfoPayInfoPayDetailList> getPayDetailList() {
        return payDetailList;
    }

    public void setPayDetailList(List<OrderInfoPayInfoPayDetailList> payDetailList) {
        this.payDetailList = payDetailList;
    }
}
