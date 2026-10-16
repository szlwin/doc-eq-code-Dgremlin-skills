package com.example.order.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 订单状态. DEC ENUM-ORDER-STATUS; YAML enum-common.yaml#enums[id=ENUM-ORDER-STATUS]; XML enum-common.xml enum[name=OrderStatus]. */
public enum OrderStatus {
    /** 待支付. DEC ENUM-VALUE-ORDER-STATUS-WAIT-PAY; YAML enum-common.yaml#enums[id=ENUM-ORDER-STATUS].values[id=ENUM-VALUE-ORDER-STATUS-WAIT-PAY]; XML enum-common.xml enum-value[name=WAIT_PAY]. */
    WAIT_PAY(1, "待支付"),
    /** 支付中. DEC ENUM-VALUE-ORDER-STATUS-PAYING; YAML enum-common.yaml#enums[id=ENUM-ORDER-STATUS].values[id=ENUM-VALUE-ORDER-STATUS-PAYING]; XML enum-common.xml enum-value[name=PAYING]. */
    PAYING(2, "支付中"),
    /** 支付成功. DEC ENUM-VALUE-ORDER-STATUS-SUCCESS; YAML enum-common.yaml#enums[id=ENUM-ORDER-STATUS].values[id=ENUM-VALUE-ORDER-STATUS-SUCCESS]; XML enum-common.xml enum-value[name=PAY_SUCCESS]. */
    PAY_SUCCESS(3, "支付成功"),
    /** 支付失败. DEC ENUM-VALUE-ORDER-STATUS-ERROR; YAML enum-common.yaml#enums[id=ENUM-ORDER-STATUS].values[id=ENUM-VALUE-ORDER-STATUS-ERROR]; XML enum-common.xml enum-value[name=PAY_ERROR]. */
    PAY_ERROR(4, "支付失败");

    private final Integer value;
    private final String description;

    OrderStatus(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static OrderStatus fromValue(Integer value) {
        if (WAIT_PAY.value.equals(value)) return WAIT_PAY;
        if (PAYING.value.equals(value)) return PAYING;
        if (PAY_SUCCESS.value.equals(value)) return PAY_SUCCESS;
        if (PAY_ERROR.value.equals(value)) return PAY_ERROR;
        throw new IllegalArgumentException("Unknown OrderStatus value: " + value);
    }
}
