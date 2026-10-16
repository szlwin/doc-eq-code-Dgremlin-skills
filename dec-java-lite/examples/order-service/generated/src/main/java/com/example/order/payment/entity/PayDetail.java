package com.example.order.payment.entity;

import java.math.BigDecimal;
import java.math.BigDecimal;

/** DEC DATA-PAY-DETAIL; YAML data-order.yaml#datas[id=DATA-PAY-DETAIL]; XML data-order.xml data[name=payDetail]. */
public class PayDetail {
    /** YAML data-order.yaml#datas[id=DATA-PAY-DETAIL].properties.id; XML data-order.xml property[name=id]. */
    private Integer id;
    /** YAML data-order.yaml#datas[id=DATA-PAY-DETAIL].properties.payId; XML data-order.xml property[name=payId]. */
    private Integer payId;
    /** YAML data-order.yaml#datas[id=DATA-PAY-DETAIL].properties.productId; XML data-order.xml property[name=productId]. */
    private Integer productId;
    /** YAML data-order.yaml#datas[id=DATA-PAY-DETAIL].properties.productName; XML data-order.xml property[name=productName]. */
    private String productName;
    /** YAML data-order.yaml#datas[id=DATA-PAY-DETAIL].properties.payPrice; XML data-order.xml property[name=payPrice]. */
    private BigDecimal payPrice;
    /** YAML data-order.yaml#datas[id=DATA-PAY-DETAIL].properties.payAmount; XML data-order.xml property[name=payAmount]. */
    private BigDecimal payAmount;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    public Integer getPayId() {
        return payId;
    }

    public void setPayId(Integer payId) {
        this.payId = payId;
    }
    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
    public BigDecimal getPayPrice() {
        return payPrice;
    }

    public void setPayPrice(BigDecimal payPrice) {
        this.payPrice = payPrice;
    }
    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }
}
