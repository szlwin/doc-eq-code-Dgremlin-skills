package com.example.expenses.expense.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.dec.lite.data.CrudMapper;
import com.example.expenses.expense.entity.Expense;

/** DEC DATA-EXPENSE; YAML data-expense.yaml#datas[id=DATA-EXPENSE]; XML data-expense.xml data[name=expense]. */
@Mapper
public interface ExpenseMapper extends CrudMapper<Expense> { }
