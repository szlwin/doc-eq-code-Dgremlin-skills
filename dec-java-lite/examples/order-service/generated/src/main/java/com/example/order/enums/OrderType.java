package com.example.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** DEC ENUM-ORDER-TYPE from enum-common.yaml. */
public enum OrderType {
    /** DEC ENUM-VALUE-ORDER-TYPE-NORMAL. */
    NORMAL("1"),
    /** DEC ENUM-VALUE-ORDER-TYPE-PREORDER. */
    PREORDER("2");

    private final String value;

    OrderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OrderType fromValue(String value) {
        if (NORMAL.value.equals(value)) return NORMAL;
        if (PREORDER.value.equals(value)) return PREORDER;
        throw new IllegalArgumentException("Unknown OrderType value: " + value);
    }
}
