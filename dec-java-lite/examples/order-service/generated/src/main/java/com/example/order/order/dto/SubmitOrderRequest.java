package com.example.order.order.dto;

import jakarta.validation.constraints.*;
import com.example.order.order.enums.OrderStatus;
import com.example.order.order.enums.OrderType;
import java.util.Objects;

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
    @AssertTrue(message = "status 与 type 组合不满足接口约束")
    public boolean isDecExpressionValid0() {
        return (com.dec.lite.data.DecValueEquality.equal(this.status == null ? null : this.status.getValue(), new java.math.BigDecimal("1")) && com.dec.lite.data.DecValueEquality.equal(this.type == null ? null : this.type.getValue(), new java.math.BigDecimal("1")));
    }

}
