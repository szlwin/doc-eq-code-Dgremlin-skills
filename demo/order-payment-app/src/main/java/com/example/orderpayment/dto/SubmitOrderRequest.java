package com.example.orderpayment.dto;

import jakarta.validation.constraints.*;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.OrderType;
import java.util.Objects;

public class SubmitOrderRequest {
    @Size(max = 500)
    @Pattern(regexp = "^[A-Za-z0-9 _.,-]{0,500}$")
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
    @NotNull
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    @NotNull
    private OrderType type;

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }
    @AssertTrue(message = "status 与 type 组合不满足接口约束")
    public boolean isDecExpressionValid0() {
        return (Objects.equals(this.status == null ? null : this.status.getValue(), "1") && Objects.equals(this.type == null ? null : this.type.getValue(), "1"));
    }

}
