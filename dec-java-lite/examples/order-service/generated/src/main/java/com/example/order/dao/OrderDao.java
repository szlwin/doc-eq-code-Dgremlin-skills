package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.Order;
import com.example.order.mapper.OrderMapper;

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
