package com.example.orderpayment.order.entity;

import java.math.BigDecimal;
import java.math.BigDecimal;
import com.example.orderpayment.order.enums.OrderStatus;
import java.time.LocalDate;
import com.example.orderpayment.order.enums.OrderType;

/** DEC DATA-ORDER; YAML data-order.yaml#datas[id=DATA-ORDER]; XML data-order.xml data[name=order]. */
public class Order {
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.id; XML data-order.xml property[name=id]. */
    private Integer id;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.userId; XML data-order.xml property[name=userId]. */
    private Integer userId;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.productCount; XML data-order.xml property[name=productCount]. */
    private Integer productCount;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.totalPrice; XML data-order.xml property[name=totalPrice]. */
    private BigDecimal totalPrice;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.totalAmount; XML data-order.xml property[name=totalAmount]. */
    private BigDecimal totalAmount;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.orderStatus; XML data-order.xml property[name=orderStatus]. */
    private OrderStatus orderStatus;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.payId; XML data-order.xml property[name=payId]. */
    private Integer payId;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.createDate; XML data-order.xml property[name=createDate]. */
    private LocalDate createDate;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.remark; XML data-order.xml property[name=remark]. */
    private String remark;
    /** YAML data-order.yaml#datas[id=DATA-ORDER].properties.orderType; XML data-order.xml property[name=orderType]. */
    private OrderType orderType;

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
    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }
    public Integer getPayId() {
        return payId;
    }

    public void setPayId(Integer payId) {
        this.payId = payId;
    }
    public LocalDate getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDate createDate) {
        this.createDate = createDate;
    }
    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }
}
