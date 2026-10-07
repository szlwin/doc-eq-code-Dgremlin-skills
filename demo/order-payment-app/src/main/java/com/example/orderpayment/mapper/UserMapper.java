package com.example.orderpayment.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.example.orderpayment.entity.User;

@Mapper
public interface UserMapper {
    User selectById(Long id);
}
