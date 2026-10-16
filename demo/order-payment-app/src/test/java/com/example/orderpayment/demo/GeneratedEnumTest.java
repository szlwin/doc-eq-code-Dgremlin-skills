package com.example.orderpayment.demo;

import com.example.orderpayment.order.enums.OrderStatus;
import com.example.orderpayment.order.enums.OrderType;
import com.example.orderpayment.payment.enums.PayResultCode;
import com.example.orderpayment.payment.enums.PaymentStatus;
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

    @Test void generatedEnumsExposeCanonicalChineseDescriptions() {
        assertEquals("待支付", OrderStatus.WAIT_PAY.getDescription());
        assertEquals("普通订单", OrderType.NORMAL.getDescription());
        assertEquals("处理中", PaymentStatus.PROCESSING.getDescription());
        assertEquals("成功", PayResultCode.SUCCESS.getDescription());
    }

    @Test void generatedEnumValuesAreIntegers() {
        assertEquals(1, OrderStatus.WAIT_PAY.getValue());
        assertEquals(1, OrderType.NORMAL.getValue());
        assertEquals(1, PaymentStatus.PROCESSING.getValue());
        assertEquals(1, PayResultCode.SUCCESS.getValue());
        assertEquals(OrderStatus.WAIT_PAY, OrderStatus.fromValue(1));
        assertEquals(PayResultCode.ERROR, PayResultCode.fromValue(2));
    }
}
