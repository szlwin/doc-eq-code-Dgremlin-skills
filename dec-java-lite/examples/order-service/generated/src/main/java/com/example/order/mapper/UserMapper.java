package com.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.order.entity.User;

@Mapper
public interface UserMapper {
    User selectById(Long id);
}
