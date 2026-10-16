package com.example.order.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 订单类型. DEC ENUM-ORDER-TYPE; YAML enum-common.yaml#enums[id=ENUM-ORDER-TYPE]; XML enum-common.xml enum[name=OrderType]. */
public enum OrderType {
    /** 普通订单. DEC ENUM-VALUE-ORDER-TYPE-NORMAL; YAML enum-common.yaml#enums[id=ENUM-ORDER-TYPE].values[id=ENUM-VALUE-ORDER-TYPE-NORMAL]; XML enum-common.xml enum-value[name=NORMAL]. */
    NORMAL(1, "普通订单"),
    /** 预售订单. DEC ENUM-VALUE-ORDER-TYPE-PREORDER; YAML enum-common.yaml#enums[id=ENUM-ORDER-TYPE].values[id=ENUM-VALUE-ORDER-TYPE-PREORDER]; XML enum-common.xml enum-value[name=PREORDER]. */
    PREORDER(2, "预售订单");

    private final Integer value;
    private final String description;

    OrderType(Integer value, String description) {
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
    public static OrderType fromValue(Integer value) {
        if (NORMAL.value.equals(value)) return NORMAL;
        if (PREORDER.value.equals(value)) return PREORDER;
        throw new IllegalArgumentException("Unknown OrderType value: " + value);
    }
}
