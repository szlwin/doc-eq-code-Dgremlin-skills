package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.OrderDetail;

@Mapper
public interface OrderDetailMapper {
    OrderDetail selectById(Long id);
}
