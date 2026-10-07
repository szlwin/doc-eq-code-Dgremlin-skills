package com.example.orderpayment.demo;

import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.OrderType;
import com.example.orderpayment.enums.PayResultCode;
import com.example.orderpayment.enums.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedEnumTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void everyCanonicalEnumValueRoundTripsThroughApiJson() throws Exception {
        for (OrderStatus value : OrderStatus.values())
            assertEquals(value, json.readValue(json.writeValueAsString(value), OrderStatus.class));
        for (OrderType value : OrderType.values())
            assertEquals(value, json.readValue(json.writeValueAsString(value), OrderType.class));
        for (PaymentStatus value : PaymentStatus.values())
            assertEquals(value, json.readValue(json.writeValueAsString(value), PaymentStatus.class));
        for (PayResultCode value : PayResultCode.values())
            assertEquals(value, json.readValue(json.writeValueAsString(value), PayResultCode.class));
    }
}
