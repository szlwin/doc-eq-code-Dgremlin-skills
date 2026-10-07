package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.Pay;

@Mapper
public interface PayMapper {
    Pay selectById(Long id);
}
