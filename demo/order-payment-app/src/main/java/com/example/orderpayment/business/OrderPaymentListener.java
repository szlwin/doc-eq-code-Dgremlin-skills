package com.example.orderpayment.business;

import com.dec.lite.action.ActionListener;
import com.dec.lite.action.ActionListenerContext;
import com.dec.lite.action.ActionResult;
import com.example.orderpayment.order.view.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** BUS-ORDER-PAYMENT; business-order-payment.yaml#business.
 * One listener receives every Action in this flow, including case and Back Actions. */
@Component
public class OrderPaymentListener implements ActionListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderPaymentListener.class);
    @Override public String businessId() { return OrderPaymentFlow.ID; }

    @Override public void before(ActionListenerContext context) {
        OrderInfo order = context.view(OrderInfo.class);
        LOG.info("Before {} in {} for order {}", context.action().name(), context.businessId(), order.getId());
        // Add Action-specific processing here using context.action().id().
    }

    @Override public void after(ActionListenerContext context, ActionResult result) {
        OrderInfo order = context.view(OrderInfo.class);
        LOG.info("After {} in {} for order {}", context.action().name(), context.businessId(), order.getId());
        if ("ACT-SMS-NOTIFY".equals(context.action().id())) {
            LOG.info("Payment error notification scheduled; awaiting transaction commit");
        }
    }
}
