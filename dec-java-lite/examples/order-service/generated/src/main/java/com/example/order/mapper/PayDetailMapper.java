package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.PayDetail;

/** DEC DATA-PAY-DETAIL from data-order.yaml; provide an adapter for the selected DataSource. */
@Mapper
public interface PayDetailMapper {
    PayDetail selectById(Long id);
    void insert(PayDetail value);
    void update(PayDetail value);
}
