package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.OrderDetail;
import com.example.order.mapper.OrderDetailMapper;

@Repository
public class OrderDetailDao {
    private final OrderDetailMapper mapper;

    public OrderDetailDao(OrderDetailMapper mapper) {
        this.mapper = mapper;
    }

    public OrderDetail find(Long id) {
        return mapper.selectById(id);
    }
}
