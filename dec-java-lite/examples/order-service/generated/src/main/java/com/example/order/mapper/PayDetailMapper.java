package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.PayDetail;

@Mapper
public interface PayDetailMapper {
    PayDetail selectById(Long id);
}
