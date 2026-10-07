package com.example.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
    INIT("0"),
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
