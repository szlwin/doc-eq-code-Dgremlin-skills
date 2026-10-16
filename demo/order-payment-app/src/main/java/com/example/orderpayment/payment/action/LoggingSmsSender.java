package com.example.orderpayment.payment.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Demo implementation; replace with an SMS service adapter when available. */
@Component
final class LoggingSmsSender implements SmsSender {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void sendPaymentError(long orderId) {
        LOG.info("Payment error notification for order {}", orderId);
    }
}
