package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.Order;

@Mapper
public interface OrderMapper {
    Order selectById(Long id);
}
