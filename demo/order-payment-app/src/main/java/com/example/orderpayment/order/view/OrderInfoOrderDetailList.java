package com.example.orderpayment.order.view;

import java.math.BigDecimal;
import com.example.orderpayment.order.enums.OrderStatus;

/** DEC OrderInfo.orderDetailList; YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList; XML view-order.xml view[name=OrderInfo]. */
public class OrderInfoOrderDetailList {
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.id; XML view-order.xml property[name=id]. */
    private Integer id;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.orderId; XML view-order.xml property[name=orderId]. */
    private Integer orderId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.userId; XML view-order.xml property[name=userId]. */
    private Integer userId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.productId; XML view-order.xml property[name=productId]. */
    private Integer productId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.productName; XML view-order.xml property[name=productName]. */
    private String productName;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.productPrice; XML view-order.xml property[name=productPrice]. */
    private BigDecimal productPrice;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.productAmount; XML view-order.xml property[name=productAmount]. */
    private BigDecimal productAmount;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList.properties.status; XML view-order.xml property[name=status]. */
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
