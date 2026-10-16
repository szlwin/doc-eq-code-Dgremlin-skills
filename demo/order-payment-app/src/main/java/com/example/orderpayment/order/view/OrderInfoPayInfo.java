package com.example.orderpayment.order.view;

import com.example.orderpayment.payment.enums.PaymentStatus;
import com.example.orderpayment.payment.enums.PayResultCode;
import java.util.List;

/** DEC OrderInfo.payInfo; YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo; XML view-order.xml view[name=OrderInfo]. */
public class OrderInfoPayInfo {
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.id; XML view-order.xml property[name=id]. */
    private Integer id;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.orderId; XML view-order.xml property[name=orderId]. */
    private Integer orderId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.userId; XML view-order.xml property[name=userId]. */
    private Integer userId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.status; XML view-order.xml property[name=status]. */
    private PaymentStatus status;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.resultCode; XML view-order.xml property[name=resultCode]. */
    private PayResultCode resultCode;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList; XML view-order.xml property[name=payDetailList]. */
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
