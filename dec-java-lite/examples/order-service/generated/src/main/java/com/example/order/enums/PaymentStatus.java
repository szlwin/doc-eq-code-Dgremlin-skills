package com.example.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** DEC ENUM-PAYMENT-STATUS from enum-common.yaml. */
public enum PaymentStatus {
    /** DEC ENUM-VALUE-PAYMENT-STATUS-INIT. */
    INIT("0"),
    /** DEC ENUM-VALUE-PAYMENT-STATUS-PROCESSING. */
    PROCESSING("1");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentStatus fromValue(String value) {
        if (INIT.value.equals(value)) return INIT;
        if (PROCESSING.value.equals(value)) return PROCESSING;
        throw new IllegalArgumentException("Unknown PaymentStatus value: " + value);
    }
}
