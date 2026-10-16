package com.example.orderpayment.payment.action;
/** Application extension point for a real SMS provider. */
public interface SmsSender {
    void sendPaymentError(long orderId);
}
