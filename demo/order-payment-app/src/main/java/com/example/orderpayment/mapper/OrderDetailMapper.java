package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.OrderDetail;

/** DEC DATA-ORDER-DETAIL from data-order.yaml; provide an adapter for the selected DataSource. */
@Mapper
public interface OrderDetailMapper {
    OrderDetail selectById(Long id);
    void insert(OrderDetail value);
    void update(OrderDetail value);
}
