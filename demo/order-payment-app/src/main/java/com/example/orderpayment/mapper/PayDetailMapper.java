package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.PayDetail;

@Mapper
public interface PayDetailMapper {
    PayDetail selectById(Long id);
}
