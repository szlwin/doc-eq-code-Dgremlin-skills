package com.example.orderpayment.order.view;

import java.math.BigDecimal;

/** DEC OrderInfoPayInfo.payDetailList; YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList; XML view-order.xml view[name=OrderInfo]. */
public class OrderInfoPayInfoPayDetailList {
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList.properties.id; XML view-order.xml property[name=id]. */
    private Integer id;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList.properties.payId; XML view-order.xml property[name=payId]. */
    private Integer payId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList.properties.productId; XML view-order.xml property[name=productId]. */
    private Integer productId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList.properties.productName; XML view-order.xml property[name=productName]. */
    private String productName;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList.properties.payPrice; XML view-order.xml property[name=payPrice]. */
    private BigDecimal payPrice;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo.properties.payDetailList.properties.payAmount; XML view-order.xml property[name=payAmount]. */
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
