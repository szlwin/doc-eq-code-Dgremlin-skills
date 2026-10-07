package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.Pay;
import com.example.order.mapper.PayMapper;

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
