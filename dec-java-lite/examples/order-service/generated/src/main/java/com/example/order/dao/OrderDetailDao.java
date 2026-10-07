package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.OrderDetail;
import com.example.order.mapper.OrderDetailMapper;

/** DEC DATA-ORDER-DETAIL from data-order.yaml; data access boundary. */
@Repository
public class OrderDetailDao {
    private final OrderDetailMapper mapper;

    public OrderDetailDao(OrderDetailMapper mapper) {
        this.mapper = mapper;
    }

    public OrderDetail find(Long id) {
        return mapper.selectById(id);
    }

    public void insert(OrderDetail value) {
        mapper.insert(value);
    }

    public void update(OrderDetail value) {
        mapper.update(value);
    }
}
