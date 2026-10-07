package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.Order;
import com.example.orderpayment.mapper.OrderMapper;

/** DEC DATA-ORDER from data-order.yaml; data access boundary. */
@Repository
public class OrderDao {
    private final OrderMapper mapper;

    public OrderDao(OrderMapper mapper) {
        this.mapper = mapper;
    }

    public Order find(Long id) {
        return mapper.selectById(id);
    }

    public void insert(Order value) {
        mapper.insert(value);
    }

    public void update(Order value) {
        mapper.update(value);
    }
}
