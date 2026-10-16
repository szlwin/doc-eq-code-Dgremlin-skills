package com.example.orderpayment.order.dto;

import jakarta.validation.constraints.*;
import com.example.orderpayment.order.enums.OrderStatus;
import com.example.orderpayment.order.enums.OrderType;
import java.math.BigDecimal;

/** DEC API-ORDER-SUBMIT; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request; XML api-order.xml api[name=submitOrder]/request. */
public class SubmitOrderRequest {
    /** DEC API-PARAM-REMARK; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-REMARK]; XML api-order.xml parameter[name=remark]. */
    @Size(max = 500, message = "订单备注长度最大为500")
    @Pattern(regexp = "^[A-Za-z0-9 _.,-]{0,500}$", message = "订单备注格式不正确")
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
    /** DEC API-PARAM-STATUS; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-STATUS]; XML api-order.xml parameter[name=status]. */
    @NotNull(message = "订单状态不能为空")
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    /** DEC API-PARAM-TYPE; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-TYPE]; XML api-order.xml parameter[name=type]. */
    @NotNull(message = "订单类型不能为空")
    private OrderType type;

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }
    /** DEC API-PARAM-USER-ID; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-USER-ID]; XML api-order.xml parameter[name=userId]. */
    @NotNull(message = "用户 ID不能为空")
    @Min(value = 1, message = "用户 ID最小为1")
    private Integer userId;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    /** DEC API-PARAM-PRODUCT-ID; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-PRODUCT-ID]; XML api-order.xml parameter[name=productId]. */
    @NotNull(message = "产品 ID不能为空")
    @Min(value = 1, message = "产品 ID最小为1")
    private Integer productId;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }
    /** DEC API-PARAM-PRODUCT-NAME; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-PRODUCT-NAME]; XML api-order.xml parameter[name=productName]. */
    @NotNull(message = "产品名称不能为空")
    @Size(min = 1, message = "产品名称长度最小为1")
    @Size(max = 120, message = "产品名称长度最大为120")
    private String productName;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
    /** DEC API-PARAM-PRODUCT-PRICE; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-PRODUCT-PRICE]; XML api-order.xml parameter[name=productPrice]. */
    @NotNull(message = "产品价格不能为空")
    @Min(value = 1, message = "产品价格最小为1元")
    private BigDecimal productPrice;

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }
    /** DEC API-PARAM-PRODUCT-COUNT; YAML api-order.yaml#apis[id=API-ORDER-SUBMIT].request.params[id=API-PARAM-PRODUCT-COUNT]; XML api-order.xml parameter[name=productCount]. */
    @NotNull(message = "产品数量不能为空")
    @Min(value = 1, message = "产品数量最小为1")
    private Integer productCount;

    public Integer getProductCount() {
        return productCount;
    }

    public void setProductCount(Integer productCount) {
        this.productCount = productCount;
    }
}
