package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.OrderDetail;

/** DEC DATA-ORDER-DETAIL from data-order.yaml; provide an adapter for the selected DataSource. */
@Mapper
public interface OrderDetailMapper {
    OrderDetail selectById(Long id);
    void insert(OrderDetail value);
    void update(OrderDetail value);
}
