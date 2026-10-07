package com.example.order.dao;

import org.springframework.stereotype.Repository;
import com.example.order.entity.User;
import com.example.order.mapper.UserMapper;

@Repository
public class UserDao {
    private final UserMapper mapper;

    public UserDao(UserMapper mapper) {
        this.mapper = mapper;
    }

    public User find(Long id) {
        return mapper.selectById(id);
    }
}
