package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.PayDetail;
import com.example.order.mapper.PayDetailMapper;

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
