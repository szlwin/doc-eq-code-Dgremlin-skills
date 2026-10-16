package com.example.orderpayment.payment.adapter;
import com.example.orderpayment.payment.enums.PayResultCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local MySQL substitute for the mongo1 insert in RULE-RECORD-PAY-ERROR. */
@Component
public final class LocalPaymentErrorAdapter {
    private final JdbcTemplate jdbc;

    public LocalPaymentErrorAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PaymentError record(int orderId, PayResultCode resultCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Payment error recording requires an active transaction");
        jdbc.update("INSERT INTO dec_demo_payment_error (order_id, result_code) VALUES (?, ?)",
                orderId, resultCode.getValue());
        return new PaymentError(orderId, resultCode);
    }

    public record PaymentError(int orderId, PayResultCode resultCode) { }
}
