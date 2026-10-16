package com.example.orderpayment.order.information;

import com.example.orderpayment.design.DecDesignMap.InformationNode;
import java.util.List;

/** Generated System-owned Information tree. Inspect ruleData/expression/changeData here; execute via InformationEngine. */
public final class OrderInformation {
    public static final String ID = "SYS-ORDER";
    public static final String NAME = "order";
    public static final String SOURCE = "systems-order.yaml#root.systems[1]";

    public static final InformationNode ORDERED = new InformationNode(
            "INFO-ORDER-ORDERED", "order.ordered", "systems-order.yaml#root.systems[1].information[0]",
            "OrderInfo", null,
            "status = 1\nand\nevery(orderDetailList, status = 1)\n",
            null,
            "status : 1;\nevery(orderDetailList, status : 1);\n",
            List.of());

    public static final InformationNode PAYABLE = new InformationNode(
            "INFO-ORDER-PAYABLE", "order.payable", "systems-order.yaml#root.systems[1].information[2]",
            null, null,
            null,
            "order.ordered\nor\norder.waitPay\n",
            null,
            List.of("order.ordered", "order.waitPay"));

    public static final InformationNode PAYING = new InformationNode(
            "INFO-ORDER-PAYING", "order.paying", "systems-order.yaml#root.systems[1].information[3]",
            "OrderInfo", null,
            "status = 2\nand\nevery(orderDetailList, status = 2)\n",
            null,
            "status : 2;\nevery(orderDetailList, status : 2);\n",
            List.of());

    public static final InformationNode PAY_ERROR_STATUS = new InformationNode(
            "INFO-ORDER-PAY-ERROR-STATUS", "order.payErrorStatus", "systems-order.yaml#root.systems[1].information[5]",
            "OrderInfo", null,
            "status = 4\nand\nevery(orderDetailList, status = 4)\n",
            null,
            "status : 4;\nevery(orderDetailList, status : 4);\n",
            List.of());

    public static final InformationNode PAY_SUCCESS_STATUS = new InformationNode(
            "INFO-ORDER-PAY-SUCCESS-STATUS", "order.paySuccessStatus", "systems-order.yaml#root.systems[1].information[4]",
            "OrderInfo", null,
            "status = 3\nand\nevery(orderDetailList, status = 3)\n",
            null,
            "status : 3;\nevery(orderDetailList, status : 3);\n",
            List.of());

    public static final InformationNode WAIT_PAY = new InformationNode(
            "INFO-ORDER-WAIT-PAY", "order.waitPay", "systems-order.yaml#root.systems[1].information[1]",
            "OrderInfo", "isWaitPay",
            null,
            null,
            null,
            List.of());

    public static final List<InformationNode> ALL = List.of(ORDERED, PAYABLE, PAYING, PAY_ERROR_STATUS, PAY_SUCCESS_STATUS, WAIT_PAY);

    private OrderInformation() { }
}
