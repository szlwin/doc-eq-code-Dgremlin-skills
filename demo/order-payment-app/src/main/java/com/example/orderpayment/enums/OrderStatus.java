package com.example.orderpayment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    WAIT_PAY("1"),
    PAYING("2"),
    PAY_SUCCESS("3"),
    PAY_ERROR("4");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OrderStatus fromValue(String value) {
        if (WAIT_PAY.value.equals(value)) return WAIT_PAY;
        if (PAYING.value.equals(value)) return PAYING;
        if (PAY_SUCCESS.value.equals(value)) return PAY_SUCCESS;
        if (PAY_ERROR.value.equals(value)) return PAY_ERROR;
        throw new IllegalArgumentException("Unknown OrderStatus value: " + value);
    }
}
