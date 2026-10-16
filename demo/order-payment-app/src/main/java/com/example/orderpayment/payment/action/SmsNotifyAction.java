package com.example.orderpayment.payment.action;
import com.dec.lite.action.ActionExecutionContext;
import com.dec.lite.action.CustomAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ACT-SMS-NOTIFY: application-owned Custom Action; no notification table is required. */
@Component
public final class SmsNotifyAction implements CustomAction {
    private final SmsSender sender;

    public SmsNotifyAction(SmsSender sender) { this.sender = sender; }

    @Override public String name() { return "smsNotify"; }

    @Override public void execute(ActionExecutionContext context) {
        Object rawId = context.model().read("id");
        if (!(rawId instanceof Number number))
            throw new IllegalStateException("smsNotify requires an order ID");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("smsNotify requires an active database transaction");
        long orderId = number.longValue();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { sender.sendPaymentError(orderId); }
        });
    }
}
