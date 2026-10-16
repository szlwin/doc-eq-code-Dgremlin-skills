package com.example.order.payment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 支付状态. DEC ENUM-PAYMENT-STATUS; YAML enum-common.yaml#enums[id=ENUM-PAYMENT-STATUS]; XML enum-common.xml enum[name=PaymentStatus]. */
public enum PaymentStatus {
    /** 初始化. DEC ENUM-VALUE-PAYMENT-STATUS-INIT; YAML enum-common.yaml#enums[id=ENUM-PAYMENT-STATUS].values[id=ENUM-VALUE-PAYMENT-STATUS-INIT]; XML enum-common.xml enum-value[name=INIT]. */
    INIT(0, "初始化"),
    /** 处理中. DEC ENUM-VALUE-PAYMENT-STATUS-PROCESSING; YAML enum-common.yaml#enums[id=ENUM-PAYMENT-STATUS].values[id=ENUM-VALUE-PAYMENT-STATUS-PROCESSING]; XML enum-common.xml enum-value[name=PROCESSING]. */
    PROCESSING(1, "处理中");

    private final Integer value;
    private final String description;

    PaymentStatus(Integer value, String description) {
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
    public static PaymentStatus fromValue(Integer value) {
        if (INIT.value.equals(value)) return INIT;
        if (PROCESSING.value.equals(value)) return PROCESSING;
        throw new IllegalArgumentException("Unknown PaymentStatus value: " + value);
    }
}
