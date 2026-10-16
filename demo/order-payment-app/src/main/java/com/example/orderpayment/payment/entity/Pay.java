package com.example.orderpayment.payment.entity;

import com.example.orderpayment.payment.enums.PaymentStatus;
import com.example.orderpayment.payment.enums.PayResultCode;

/** DEC DATA-PAY; YAML data-order.yaml#datas[id=DATA-PAY]; XML data-order.xml data[name=pay]. */
public class Pay {
    /** YAML data-order.yaml#datas[id=DATA-PAY].properties.id; XML data-order.xml property[name=id]. */
    private Integer id;
    /** YAML data-order.yaml#datas[id=DATA-PAY].properties.orderId; XML data-order.xml property[name=orderId]. */
    private Integer orderId;
    /** YAML data-order.yaml#datas[id=DATA-PAY].properties.userId; XML data-order.xml property[name=userId]. */
    private Integer userId;
    /** YAML data-order.yaml#datas[id=DATA-PAY].properties.status; XML data-order.xml property[name=status]. */
    private PaymentStatus status;
    /** YAML data-order.yaml#datas[id=DATA-PAY].properties.resultCode; XML data-order.xml property[name=resultCode]. */
    private PayResultCode resultCode;

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
}
