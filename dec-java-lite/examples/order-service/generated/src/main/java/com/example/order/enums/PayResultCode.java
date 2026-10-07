package com.example.order.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PayResultCode {
    SUCCESS("SUCCESS"),
    ERROR("ERROR");

    private final String value;

    PayResultCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PayResultCode fromValue(String value) {
        if (SUCCESS.value.equals(value)) return SUCCESS;
        if (ERROR.value.equals(value)) return ERROR;
        throw new IllegalArgumentException("Unknown PayResultCode value: " + value);
    }
}
