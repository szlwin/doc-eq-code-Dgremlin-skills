package com.example.order.business;

import com.example.order.design.DecDesignMap.*;
import java.util.List;

/** Generated Business flow: ordered dependencies/actions/produces/change and execution/case/back edges.
 * Use DirectoryNode.name() with DirectoryWorkflowRunner; this class does not execute or duplicate the state machine. */
public final class OrderPaymentFlow {
    public static final String ID = "BUS-ORDER-PAYMENT";
    public static final String NAME = "order-payment";
    public static final String SOURCE = "business-order-payment.yaml#root.business";
    public static final String DATA_SOURCE = null;

    public static final DirectoryNode ORDERED = new DirectoryNode(
            "DIR-ORDERED", "ordered", "business-order-payment.yaml#root.business.directories[0]", "normal",
            "order.ordered", "OrderInfo", true,
            List.of(),
            List.of(),
            List.of(
                        new ActionNode("ACT-SAVE-ORDER", "saveOrder", "business-order-payment.yaml#root.business.directories[0].actions[0]", "order", "saveOrder", null, null, DATA_SOURCE,
                                List.of(new ProduceNode("PROD-ORDER", "OrderInfo", "order.ordered")))),
            null);

    public static final DirectoryNode PAYING = new DirectoryNode(
            "DIR-PAYING", "paying", "business-order-payment.yaml#root.business.directories[1]", "normal",
            "order.paying", "OrderInfo", false,
            List.of("order.payable", "user.effective"),
            List.of(
                    new DirectoryEdge("ordered", "execution", null, null, List.of())),
            List.of(
                        new ActionNode("ACT-START-PAY", "startPay", "business-order-payment.yaml#root.business.directories[1].actions[0]", "payment", "pay", null, null, DATA_SOURCE,
                                List.of(new ProduceNode("PROD-PAYMENT-INFO", "PaymentInfo", "payment.paymentInfo")))),
            "order.paying");

    public static final DirectoryNode PAY_RESULT = new DirectoryNode(
            "DIR-PAY-RESULT", "PayResult", "business-order-payment.yaml#root.business.directories[2]", "result",
            "payment.hasResult", "OrderInfo", false,
            List.of(),
            List.of(
                    new DirectoryEdge("paying", "execution", null, "returnPaying", List.of(
                        new ActionNode("ACT-RESET-PAY-RESULT", "resetPayResult", "business-order-payment.yaml#root.business.directories[2].subDirectories[0].back.actions[0]", "payment", "resetPayResult", null, null, DATA_SOURCE,
                                List.of()))),
                    new DirectoryEdge("success", "case", "payment.success", null, List.of()),
                    new DirectoryEdge("error", "case", "payment.error", null, List.of())),
            List.of(
                        new ActionNode("ACT-RECEIVE-PAY-RESULT", "receivePayResult", "business-order-payment.yaml#root.business.directories[2].actions[0]", "payment", "receivePayResult", null, null, DATA_SOURCE,
                                List.of(new ProduceNode("PROD-PAY-RESULT", "PayResult", "payment.hasResult")))),
            null);

    public static final DirectoryNode SUCCESS = new DirectoryNode(
            "DIR-SUCCESS", "success", "business-order-payment.yaml#root.business.directories[3]", "normal",
            "common.paySuccess", "OrderInfo", false,
            List.of(),
            List.of(),
            List.of(
                        new ActionNode("ACT-CONFIRM-PAY-SUCCESS", "confirmPaySuccess", "business-order-payment.yaml#root.business.directories[3].actions[0]", "order", "confirmPaySuccess", null, null, DATA_SOURCE,
                                List.of())),
            "order.paySuccessStatus");

    public static final DirectoryNode ERROR = new DirectoryNode(
            "DIR-ERROR", "error", "business-order-payment.yaml#root.business.directories[4]", "normal",
            "common.payError", "OrderInfo", false,
            List.of(),
            List.of(),
            List.of(
                        new ActionNode("ACT-RECORD-PAY-ERROR", "recordPayError", "business-order-payment.yaml#root.business.directories[4].actions[0]", "payment", "recordPayError", null, null, DATA_SOURCE,
                                List.of(new ProduceNode("PROD-PAY-ERROR", "PayError", null))),
                        new ActionNode("ACT-SMS-NOTIFY", "smsNotify", "business-order-payment.yaml#root.business.directories[4].actions[1]", null, null, "smsNotify", null, DATA_SOURCE,
                                List.of()),
                        new ActionNode("ACT-CONFIRM-PAY-ERROR", "confirmPayError", "business-order-payment.yaml#root.business.directories[4].actions[2]", "order", "confirmPayError", null, null, DATA_SOURCE,
                                List.of())),
            "order.payErrorStatus");

    public static final List<DirectoryNode> ALL = List.of(ORDERED, PAYING, PAY_RESULT, SUCCESS, ERROR);

    private OrderPaymentFlow() { }
}
