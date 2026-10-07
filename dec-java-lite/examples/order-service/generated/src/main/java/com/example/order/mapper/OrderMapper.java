package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.Order;

@Mapper
public interface OrderMapper {
    Order selectById(Long id);
}
