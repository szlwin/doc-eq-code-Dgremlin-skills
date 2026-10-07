package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.Order;
import com.example.orderpayment.mapper.OrderMapper;

@Repository
public class OrderDao {
    private final OrderMapper mapper;

    public OrderDao(OrderMapper mapper) {
        this.mapper = mapper;
    }

    public Order find(Long id) {
        return mapper.selectById(id);
    }
}
