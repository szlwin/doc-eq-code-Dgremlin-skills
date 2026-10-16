package com.example.expenses.expense.information;

import com.example.expenses.design.DecDesignMap.InformationNode;
import java.util.List;

/** Generated System-owned Information tree. Inspect ruleData/expression/changeData here; execute via InformationEngine. */
public final class ExpenseInformation {
    public static final String ID = "SYS-EXPENSE";
    public static final String NAME = "expense";
    public static final String SOURCE = "systems-expense.yaml#root.systems[0]";

    public static final InformationNode APPROVED = new InformationNode(
            "INFO-EXPENSE-APPROVED", "expense.approved", "systems-expense.yaml#root.systems[0].information[2]",
            "ExpenseInfo", null,
            "status = 2",
            null,
            "status : 2;",
            List.of());

    public static final InformationNode DRAFT = new InformationNode(
            "INFO-EXPENSE-DRAFT", "expense.draft", "systems-expense.yaml#root.systems[0].information[0]",
            "ExpenseInfo", null,
            "status = 0",
            null,
            "status : 0;",
            List.of());

    public static final InformationNode ELIGIBLE = new InformationNode(
            "INFO-EXPENSE-ELIGIBLE", "expense.eligible", "systems-expense.yaml#root.systems[0].information[3]",
            "ExpenseInfo", null,
            "amount <= 1000 and reviewerEnabled = true",
            null,
            null,
            List.of());

    public static final InformationNode SUBMITTED = new InformationNode(
            "INFO-EXPENSE-SUBMITTED", "expense.submitted", "systems-expense.yaml#root.systems[0].information[1]",
            "ExpenseInfo", null,
            "status = 1",
            null,
            "status : 1;",
            List.of());

    public static final List<InformationNode> ALL = List.of(APPROVED, DRAFT, ELIGIBLE, SUBMITTED);

    private ExpenseInformation() { }
}
