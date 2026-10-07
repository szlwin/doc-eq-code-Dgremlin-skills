package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.Pay;

/** DEC DATA-PAY from data-order.yaml; provide an adapter for the selected DataSource. */
@Mapper
public interface PayMapper {
    Pay selectById(Long id);
    void insert(Pay value);
    void update(Pay value);
}
