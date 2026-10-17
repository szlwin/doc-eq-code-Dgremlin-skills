package com.example.expenses.business;

import com.example.expenses.design.DecDesignMap.*;
import java.util.List;

/** Generated Business flow: ordered dependencies/actions/produces/change and execution/case/back edges.
 * Use DirectoryNode.name() with DirectoryWorkflowRunner; this class does not execute or duplicate the state machine. */
public final class ExpenseApprovalFlow {
    public static final String ID = "BUS-EXPENSE";
    public static final String NAME = "expense-approval";
    public static final String SOURCE = "business-expense.yaml#root.business";
    public static final String DATA_SOURCE = null;

    public static final DirectoryNode DRAFT = new DirectoryNode(
            "DIR-EXPENSE-DRAFT", "draft", "business-expense.yaml#root.business.directories[0]", "normal",
            "expense.draft", "ExpenseInfo", true,
            List.of(),
            List.of(),
            List.of(),
            "expense.draft");

    public static final DirectoryNode SUBMITTED = new DirectoryNode(
            "DIR-EXPENSE-SUBMITTED", "submitted", "business-expense.yaml#root.business.directories[1]", "normal",
            "expense.submitted", "ExpenseInfo", false,
            List.of(),
            List.of(
                    new DirectoryEdge("draft", "execution", null, null, List.of())),
            List.of(
                        new ActionNode("ACT-EXPENSE-SUBMITTED", "recordSubmitted", "business-expense.yaml#root.business.directories[1].actions[0]", "expense", null, "recordSubmitted", null, DATA_SOURCE,
                                List.of())),
            "expense.submitted");

    public static final DirectoryNode APPROVED = new DirectoryNode(
            "DIR-EXPENSE-APPROVED", "approved", "business-expense.yaml#root.business.directories[2]", "normal",
            "expense.approved", "ExpenseInfo", false,
            List.of("expense.eligible"),
            List.of(
                    new DirectoryEdge("submitted", "execution", null, null, List.of())),
            List.of(
                        new ActionNode("ACT-EXPENSE-APPROVED", "recordApproved", "business-expense.yaml#root.business.directories[2].actions[0]", "expense", null, "recordApproved", null, DATA_SOURCE,
                                List.of())),
            "expense.approved");

    public static final List<DirectoryNode> ALL = List.of(DRAFT, SUBMITTED, APPROVED);

    private ExpenseApprovalFlow() { }
}
