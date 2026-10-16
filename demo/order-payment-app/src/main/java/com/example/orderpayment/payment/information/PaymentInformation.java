package com.example.orderpayment.payment.information;

import com.example.orderpayment.design.DecDesignMap.InformationNode;
import java.util.List;

/** Generated System-owned Information tree. Inspect ruleData/expression/changeData here; execute via InformationEngine. */
public final class PaymentInformation {
    public static final String ID = "SYS-PAYMENT";
    public static final String NAME = "payment";
    public static final String SOURCE = "systems-order.yaml#root.systems[2]";

    public static final InformationNode COMPLETED = new InformationNode(
            "INFO-PAYMENT-COMPLETED", "payment.completed", "systems-order.yaml#root.systems[2].information[4]",
            null, null,
            null,
            "payment.success\nor\npayment.error\n",
            null,
            List.of("payment.error", "payment.success"));

    public static final InformationNode ERROR = new InformationNode(
            "INFO-PAYMENT-ERROR", "payment.error", "systems-order.yaml#root.systems[2].information[3]",
            "OrderInfo", "isPayError",
            null,
            null,
            null,
            List.of());

    public static final InformationNode HAS_RESULT = new InformationNode(
            "INFO-PAYMENT-HAS-RESULT", "payment.hasResult", "systems-order.yaml#root.systems[2].information[1]",
            "OrderInfo", "hasPayResult",
            null,
            null,
            null,
            List.of());

    public static final InformationNode PAYMENT_INFO = new InformationNode(
            "INFO-PAYMENT-INFO", "payment.paymentInfo", "systems-order.yaml#root.systems[2].information[0]",
            "OrderInfo", "hasPaymentInfo",
            null,
            null,
            null,
            List.of());

    public static final InformationNode PROCESSING = new InformationNode(
            "INFO-PAYMENT-PROCESSING", "payment.processing", "systems-order.yaml#root.systems[2].information[5]",
            "OrderInfo", null,
            "payInfo.status = 1",
            null,
            null,
            List.of());

    public static final InformationNode SUCCESS = new InformationNode(
            "INFO-PAYMENT-SUCCESS", "payment.success", "systems-order.yaml#root.systems[2].information[2]",
            "OrderInfo", "isPaySuccess",
            null,
            null,
            null,
            List.of());

    public static final List<InformationNode> ALL = List.of(COMPLETED, ERROR, HAS_RESULT, PAYMENT_INFO, PROCESSING, SUCCESS);

    private PaymentInformation() { }
}
