package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.Pay;
import com.example.orderpayment.mapper.PayMapper;

@Repository
public class PayDao {
    private final PayMapper mapper;

    public PayDao(PayMapper mapper) {
        this.mapper = mapper;
    }

    public Pay find(Long id) {
        return mapper.selectById(id);
    }
}
