package com.example.orderpayment.order.view;

import java.math.BigDecimal;
import com.example.orderpayment.order.enums.OrderStatus;
import java.time.LocalDate;
import java.util.List;
import com.example.orderpayment.order.enums.OrderType;

/** DEC VIEW-ORDER-INFO; YAML view-order.yaml#views[id=VIEW-ORDER-INFO]; XML view-order.xml view[name=OrderInfo]. */
public class OrderInfo {
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.id; XML view-order.xml property[name=id]. */
    private Integer id;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.userId; XML view-order.xml property[name=userId]. */
    private Integer userId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.productCount; XML view-order.xml property[name=productCount]. */
    private Integer productCount;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.totalPrice; XML view-order.xml property[name=totalPrice]. */
    private BigDecimal totalPrice;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.totalAmount; XML view-order.xml property[name=totalAmount]. */
    private BigDecimal totalAmount;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.status; XML view-order.xml property[name=status]. */
    private OrderStatus status;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payId; XML view-order.xml property[name=payId]. */
    private Integer payId;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.dateTime; XML view-order.xml property[name=dateTime]. */
    private LocalDate dateTime;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.user; XML view-order.xml property[name=user]. */
    private OrderInfoUser user;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.orderDetailList; XML view-order.xml property[name=orderDetailList]. */
    private List<OrderInfoOrderDetailList> orderDetailList;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.payInfo; XML view-order.xml property[name=payInfo]. */
    private OrderInfoPayInfo payInfo;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.remark; XML view-order.xml property[name=remark]. */
    private String remark;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.type; XML view-order.xml property[name=type]. */
    private OrderType type;

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
    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }
}
