package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.PayDetail;
import com.example.orderpayment.mapper.PayDetailMapper;

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
