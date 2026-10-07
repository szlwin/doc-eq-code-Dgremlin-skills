package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.Pay;

@Mapper
public interface PayMapper {
    Pay selectById(Long id);
}
