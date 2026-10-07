package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.Pay;
import com.example.order.mapper.PayMapper;

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
