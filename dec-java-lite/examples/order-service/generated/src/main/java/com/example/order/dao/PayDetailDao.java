package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.PayDetail;
import com.example.order.mapper.PayDetailMapper;

/** DEC DATA-PAY-DETAIL from data-order.yaml; data access boundary. */
@Repository
public class PayDetailDao {
    private final PayDetailMapper mapper;

    public PayDetailDao(PayDetailMapper mapper) {
        this.mapper = mapper;
    }

    public PayDetail find(Long id) {
        return mapper.selectById(id);
    }

    public void insert(PayDetail value) {
        mapper.insert(value);
    }

    public void update(PayDetail value) {
        mapper.update(value);
    }
}
