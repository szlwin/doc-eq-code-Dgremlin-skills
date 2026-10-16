package com.example.orderpayment.user.information;

import com.example.orderpayment.design.DecDesignMap.InformationNode;
import java.util.List;

/** Generated System-owned Information tree. Inspect ruleData/expression/changeData here; execute via InformationEngine. */
public final class UserInformation {
    public static final String ID = "SYS-USER";
    public static final String NAME = "user";
    public static final String SOURCE = "systems-order.yaml#root.systems[0]";

    public static final InformationNode ACTIVATED = new InformationNode(
            "INFO-USER-ACTIVATED", "user.activated", "systems-order.yaml#root.systems[0].information[0]",
            "UserInfo", "isActivated",
            null,
            null,
            null,
            List.of());

    public static final InformationNode CERTIFIED = new InformationNode(
            "INFO-USER-CERTIFIED", "user.certified", "systems-order.yaml#root.systems[0].information[1]",
            "UserInfo", "isCertified",
            null,
            null,
            null,
            List.of());

    public static final InformationNode EFFECTIVE = new InformationNode(
            "INFO-USER-EFFECTIVE", "user.effective", "systems-order.yaml#root.systems[0].information[2]",
            null, null,
            null,
            "user.activated\nand\nuser.certified\n",
            null,
            List.of("user.activated", "user.certified"));

    public static final List<InformationNode> ALL = List.of(ACTIVATED, CERTIFIED, EFFECTIVE);

    private UserInformation() { }
}
