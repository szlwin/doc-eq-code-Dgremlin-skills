package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.User;

/** DEC DATA-USER from data-order.yaml; provide an adapter for the selected DataSource. */
@Mapper
public interface UserMapper {
    User selectById(Long id);
    void insert(User value);
    void update(User value);
}
