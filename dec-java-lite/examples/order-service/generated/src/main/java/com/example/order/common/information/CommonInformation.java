package com.example.order.common.information;

import com.example.order.design.DecDesignMap.InformationNode;
import java.util.List;

/** Generated System-owned Information tree. Inspect ruleData/expression/changeData here; execute via InformationEngine. */
public final class CommonInformation {
    public static final String ID = "SYS-COMMON";
    public static final String NAME = "common";
    public static final String SOURCE = "systems-order.yaml#root.systems[3]";

    public static final InformationNode PAY_ERROR = new InformationNode(
            "INFO-COMMON-PAY-ERROR", "common.payError", "systems-order.yaml#root.systems[3].information[1]",
            null, null,
            null,
            "payment.error\nand\norder.payErrorStatus\n",
            null,
            List.of("order.payErrorStatus", "payment.error"));

    public static final InformationNode PAY_SUCCESS = new InformationNode(
            "INFO-COMMON-PAY-SUCCESS", "common.paySuccess", "systems-order.yaml#root.systems[3].information[0]",
            null, null,
            null,
            "payment.success\nand\norder.paySuccessStatus\n",
            null,
            List.of("order.paySuccessStatus", "payment.success"));

    public static final List<InformationNode> ALL = List.of(PAY_ERROR, PAY_SUCCESS);

    private CommonInformation() { }
}
