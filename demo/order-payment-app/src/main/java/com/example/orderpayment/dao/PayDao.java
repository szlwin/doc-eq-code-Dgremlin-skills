package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.Pay;
import com.example.orderpayment.mapper.PayMapper;

/** DEC DATA-PAY from data-order.yaml; data access boundary. */
@Repository
public class PayDao {
    private final PayMapper mapper;

    public PayDao(PayMapper mapper) {
        this.mapper = mapper;
    }

    public Pay find(Long id) {
        return mapper.selectById(id);
    }

    public void insert(Pay value) {
        mapper.insert(value);
    }

    public void update(Pay value) {
        mapper.update(value);
    }
}
