package com.example.order.payment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 支付结果. DEC ENUM-PAY-RESULT-CODE; YAML enum-common.yaml#enums[id=ENUM-PAY-RESULT-CODE]; XML enum-common.xml enum[name=PayResultCode]. */
public enum PayResultCode {
    /** 成功. DEC ENUM-VALUE-PAY-RESULT-SUCCESS; YAML enum-common.yaml#enums[id=ENUM-PAY-RESULT-CODE].values[id=ENUM-VALUE-PAY-RESULT-SUCCESS]; XML enum-common.xml enum-value[name=SUCCESS]. */
    SUCCESS(1, "成功"),
    /** 失败. DEC ENUM-VALUE-PAY-RESULT-ERROR; YAML enum-common.yaml#enums[id=ENUM-PAY-RESULT-CODE].values[id=ENUM-VALUE-PAY-RESULT-ERROR]; XML enum-common.xml enum-value[name=ERROR]. */
    ERROR(2, "失败");

    private final Integer value;
    private final String description;

    PayResultCode(Integer value, String description) {
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
    public static PayResultCode fromValue(Integer value) {
        if (SUCCESS.value.equals(value)) return SUCCESS;
        if (ERROR.value.equals(value)) return ERROR;
        throw new IllegalArgumentException("Unknown PayResultCode value: " + value);
    }
}
