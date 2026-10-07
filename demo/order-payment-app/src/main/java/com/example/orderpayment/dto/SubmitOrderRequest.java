package com.example.orderpayment.dto;

import jakarta.validation.constraints.*;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.OrderType;
import java.math.BigDecimal;
import java.util.Objects;

/** DEC API-ORDER-SUBMIT from api-order.yaml. */
public class SubmitOrderRequest {
    /** DEC API-PARAM-REMARK. */
    @Size(max = 500)
    @Pattern(regexp = "^[A-Za-z0-9 _.,-]{0,500}$")
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
    /** DEC API-PARAM-STATUS. */
    @NotNull
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    /** DEC API-PARAM-TYPE. */
    @NotNull
    private OrderType type;

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }
    /** DEC API-PARAM-USER-ID. */
    @NotNull
    @Min(1)
    private Integer userId;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    /** DEC API-PARAM-PRODUCT-ID. */
    @NotNull
    @Min(1)
    private Integer productId;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }
    /** DEC API-PARAM-PRODUCT-NAME. */
    @NotNull
    @Size(min = 1)
    @Size(max = 120)
    private String productName;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
    /** DEC API-PARAM-PRODUCT-PRICE. */
    @NotNull
    @Min(1)
    private BigDecimal productPrice;

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }
    /** DEC API-PARAM-PRODUCT-COUNT. */
    @NotNull
    @Min(1)
    private Integer productCount;

    public Integer getProductCount() {
        return productCount;
    }

    public void setProductCount(Integer productCount) {
        this.productCount = productCount;
    }
    @AssertTrue(message = "status 与 type 组合不满足接口约束")
    public boolean isDecExpressionValid0() {
        return (Objects.equals(this.status == null ? null : this.status.getValue(), "1") && Objects.equals(this.type == null ? null : this.type.getValue(), "1"));
    }

}
