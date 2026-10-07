package com.example.orderpayment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderType {
    NORMAL("1"),
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
