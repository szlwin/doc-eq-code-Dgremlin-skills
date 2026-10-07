package com.example.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** DEC ENUM-ORDER-STATUS from enum-common.yaml. */
public enum OrderStatus {
    /** DEC ENUM-VALUE-ORDER-STATUS-WAIT-PAY. */
    WAIT_PAY("1"),
    /** DEC ENUM-VALUE-ORDER-STATUS-PAYING. */
    PAYING("2"),
    /** DEC ENUM-VALUE-ORDER-STATUS-SUCCESS. */
    PAY_SUCCESS("3"),
    /** DEC ENUM-VALUE-ORDER-STATUS-ERROR. */
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
