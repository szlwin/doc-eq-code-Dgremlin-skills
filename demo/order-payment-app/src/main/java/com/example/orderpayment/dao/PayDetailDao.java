package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.PayDetail;
import com.example.orderpayment.mapper.PayDetailMapper;

@Repository
public class PayDetailDao {
    private final PayDetailMapper mapper;

    public PayDetailDao(PayDetailMapper mapper) {
        this.mapper = mapper;
    }

    public PayDetail find(Long id) {
        return mapper.selectById(id);
    }
}
