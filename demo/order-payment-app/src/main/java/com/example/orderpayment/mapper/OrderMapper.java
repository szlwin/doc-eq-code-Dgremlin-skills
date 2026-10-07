package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.Order;

/** DEC DATA-ORDER from data-order.yaml; provide an adapter for the selected DataSource. */
@Mapper
public interface OrderMapper {
    Order selectById(Long id);
    void insert(Order value);
    void update(Order value);
}
