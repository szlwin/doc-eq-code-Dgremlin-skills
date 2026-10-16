package com.example.expenses.expense.entity;

import java.math.BigDecimal;

/** DEC DATA-EXPENSE; YAML data-expense.yaml#datas[id=DATA-EXPENSE]; XML data-expense.xml data[name=expense]. */
public class Expense {
    /** YAML data-expense.yaml#datas[id=DATA-EXPENSE].properties.id; XML data-expense.xml property[name=id]. */
    private Integer id;
    /** YAML data-expense.yaml#datas[id=DATA-EXPENSE].properties.status; XML data-expense.xml property[name=status]. */
    private Integer status;
    /** YAML data-expense.yaml#datas[id=DATA-EXPENSE].properties.amount; XML data-expense.xml property[name=amount]. */
    private BigDecimal amount;
    /** YAML data-expense.yaml#datas[id=DATA-EXPENSE].properties.reviewerEnabled; XML data-expense.xml property[name=reviewerEnabled]. */
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
