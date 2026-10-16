package com.example.expenses.expense.dao;

import org.springframework.stereotype.Repository;
import com.dec.lite.data.CrudDao;
import com.example.expenses.expense.entity.Expense;
import com.example.expenses.expense.mapper.ExpenseMapper;

/** DEC DATA-EXPENSE; YAML data-expense.yaml#datas[id=DATA-EXPENSE]; XML data-expense.xml data[name=expense]. */
@Repository
public class ExpenseDao extends CrudDao<Expense> {
    public ExpenseDao(ExpenseMapper mapper) {
        super(mapper);
    }
}
