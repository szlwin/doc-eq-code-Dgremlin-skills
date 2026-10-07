package com.example.orderpayment.dao;

import org.springframework.stereotype.Repository;
import com.example.orderpayment.entity.User;
import com.example.orderpayment.mapper.UserMapper;

/** DEC DATA-USER from data-order.yaml; data access boundary. */
@Repository
public class UserDao {
    private final UserMapper mapper;

    public UserDao(UserMapper mapper) {
        this.mapper = mapper;
    }

    public User find(Long id) {
        return mapper.selectById(id);
    }

    public void insert(User value) {
        mapper.insert(value);
    }

    public void update(User value) {
        mapper.update(value);
    }
}
