package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.OrderDetail;

@Mapper
public interface OrderDetailMapper {
    OrderDetail selectById(Long id);
}
