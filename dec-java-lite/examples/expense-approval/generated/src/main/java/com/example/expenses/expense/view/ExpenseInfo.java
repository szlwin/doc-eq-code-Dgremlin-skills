package com.example.expenses.expense.view;

import java.math.BigDecimal;

/** DEC VIEW-EXPENSE; YAML view-expense.yaml#views[id=VIEW-EXPENSE]; XML view-expense.xml view[name=ExpenseInfo]. */
public class ExpenseInfo {
    /** YAML view-expense.yaml#views[id=VIEW-EXPENSE].properties.id; XML view-expense.xml property[name=id]. */
    private Integer id;
    /** YAML view-expense.yaml#views[id=VIEW-EXPENSE].properties.status; XML view-expense.xml property[name=status]. */
    private Integer status;
    /** YAML view-expense.yaml#views[id=VIEW-EXPENSE].properties.amount; XML view-expense.xml property[name=amount]. */
    private BigDecimal amount;
    /** YAML view-expense.yaml#views[id=VIEW-EXPENSE].properties.reviewerEnabled; XML view-expense.xml property[name=reviewerEnabled]. */
    private Boolean reviewerEnabled;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    public Boolean getReviewerEnabled() {
        return reviewerEnabled;
    }

    public void setReviewerEnabled(Boolean reviewerEnabled) {
        this.reviewerEnabled = reviewerEnabled;
    }
}
