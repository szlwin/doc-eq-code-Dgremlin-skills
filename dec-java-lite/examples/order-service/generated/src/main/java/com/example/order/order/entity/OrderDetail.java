package com.example.order.order.entity;

import java.math.BigDecimal;
import java.math.BigDecimal;
import com.example.order.order.enums.OrderStatus;

/** DEC DATA-ORDER-DETAIL; YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL]; XML data-order.xml data[name=orderDetail]. */
public class OrderDetail {
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.id; XML data-order.xml property[name=id]. */
    private Integer id;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.orderId; XML data-order.xml property[name=orderId]. */
    private Integer orderId;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.userId; XML data-order.xml property[name=userId]. */
    private Integer userId;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.productId; XML data-order.xml property[name=productId]. */
    private Integer productId;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.productName; XML data-order.xml property[name=productName]. */
    private String productName;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.productPrice; XML data-order.xml property[name=productPrice]. */
    private BigDecimal productPrice;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.productAmount; XML data-order.xml property[name=productAmount]. */
    private BigDecimal productAmount;
    /** YAML data-order.yaml#datas[id=DATA-ORDER-DETAIL].properties.status; XML data-order.xml property[name=status]. */
    private OrderStatus status;

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
    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }
    public BigDecimal getProductAmount() {
        return productAmount;
    }

    public void setProductAmount(BigDecimal productAmount) {
        this.productAmount = productAmount;
    }
    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
